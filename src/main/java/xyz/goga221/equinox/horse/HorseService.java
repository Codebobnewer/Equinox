package xyz.goga221.equinox.horse;

import xyz.goga221.equinox.config.ConfigManager;
import xyz.goga221.equinox.config.TierDefinition;
import xyz.goga221.equinox.economy.EconomyProvider;
import xyz.goga221.equinox.station.Station;
import xyz.goga221.equinox.station.StationService;
import xyz.goga221.equinox.station.WorldGuardHook;
import xyz.goga221.equinox.util.Messages;
import com.destroystokyo.paper.entity.Pathfinder;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Owns the whole purchase/sell lifecycle: ownership checks, spending/refunding through
 * {@link EconomyProvider}, spawning/removing the actual {@link Horse} entity, and tagging
 * it so {@link HorseListener} can enforce exclusive riding and clean up on death.
 */
public final class HorseService {

    // How often the follow loop re-evaluates every owned horse's distance to its owner.
    private static final long FOLLOW_PERIOD_TICKS = 20L;
    // Matches vanilla's wolf FollowOwnerGoal thresholds: start walking once far enough away,
    // keep going until close again, rather than flickering on/off right at one boundary.
    private static final double FOLLOW_START_DISTANCE = 10.0;
    private static final double FOLLOW_STOP_DISTANCE = 3.0;
    private static final double FOLLOW_SPEED = 1.0;
    // Matches vanilla's wolf TeleportToOwnerGoal: too far to realistically walk back, so it
    // teleports to a safe spot near the owner instead of trudging across the map.
    private static final double FOLLOW_TELEPORT_DISTANCE = 12.0;
    private static final int TELEPORT_SEARCH_ATTEMPTS = 10;
    private static final int TELEPORT_SEARCH_RADIUS = 3;

    private final Plugin plugin;
    private final ConfigManager config;
    private final HorseRepository horseRepository;
    private final StationService stationService;
    private final WorldGuardHook worldGuardHook;
    private final EconomyProvider economy;
    private final TaskScheduler scheduler;
    private final Messages messages;

    private final NamespacedKey ownerKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey stayKey;

    // Ownership is checked constantly (every purchase, sell and /stable open) but changes rarely,
    // so it's kept in memory - keyed by owner UUID - instead of hitting the database on those hot
    // paths. Seeded once from the repository at startup; every purchase/sell/death keeps it in sync.
    private final Map<UUID, OwnedHorse> ownedHorses = new ConcurrentHashMap<>();

    // Claimed synchronously at the start of purchase(), released once spawnPurchasedHorse() either
    // finishes or fails. The horse doesn't actually land in ownedHorses until that (deferred, async)
    // spawn task runs, so without this a player could pass the "already own a horse" check twice by
    // re-running /stable and buying again before the first purchase's spawn task ever executes.
    private final Set<UUID> pendingPurchases = ConcurrentHashMap.newKeySet();

    public HorseService(Plugin plugin, ConfigManager config, HorseRepository horseRepository, StationService stationService,
                         WorldGuardHook worldGuardHook, EconomyProvider economy, TaskScheduler scheduler, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.horseRepository = horseRepository;
        this.stationService = stationService;
        this.worldGuardHook = worldGuardHook;
        this.economy = economy;
        this.scheduler = scheduler;
        this.messages = messages;
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.tierKey = new NamespacedKey(plugin, "tier");
        this.stayKey = new NamespacedKey(plugin, "stay");
        startFollowLoop();
    }

    /** Loads every owned horse into the cache off the main thread, returning how many were loaded. */
    public CompletableFuture<Integer> loadOwnedHorsesIntoCache() {
        return horseRepository.loadAll().thenApply(loaded -> {
            ownedHorses.clear();
            for (OwnedHorse owned : loaded) {
                ownedHorses.put(owned.getOwnerUuid(), owned);
            }
            return ownedHorses.size();
        });
    }

    public boolean hasHorse(UUID playerUuid) {
        return ownedHorses.containsKey(playerUuid);
    }

    public void purchase(Player player, HorseTier tier) {
        UUID playerUuid = player.getUniqueId();
        // pendingPurchases.add() is the actual guard against a double-buy: it's claimed here,
        // synchronously, before any of the async work below, and only released once
        // spawnPurchasedHorse() finishes - see the field's own comment for why that matters.
        if (ownedHorses.containsKey(playerUuid) || !pendingPurchases.add(playerUuid)) {
            messages.send(player, "already-own-horse");
            return;
        }

        TierDefinition definition = config.getTier(tier);
        if (definition == null) {
            pendingPurchases.remove(playerUuid);
            return;
        }

        Optional<Station> station = stationService.findNearestBuyStation(player.getLocation());
        if (station.isEmpty()) {
            pendingPurchases.remove(playerUuid);
            messages.send(player, "no-buy-station");
            return;
        }

        if (!economy.has(player, definition.getPrice())) {
            pendingPurchases.remove(playerUuid);
            messages.send(player, "not-enough-money");
            return;
        }

        // center() is null if the station's world can't be resolved right now - checked before
        // withdrawing so a station with a bad/unloaded world can't charge the player for nothing.
        Location spawnLocation = station.get().center();
        if (spawnLocation == null) {
            pendingPurchases.remove(playerUuid);
            messages.send(player, "no-buy-station");
            return;
        }

        economy.withdraw(player, definition.getPrice());
        scheduler.execute(spawnLocation, () -> spawnPurchasedHorse(player, tier, definition, spawnLocation));
    }

    private void spawnPurchasedHorse(Player player, HorseTier tier, TierDefinition definition, Location spawnLocation) {
        try {
            Horse horse = (Horse) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.HORSE);
            horse.setColor(definition.getColor());
            horse.setStyle(definition.getStyle());
            horse.setTamed(true);
            horse.setOwner(player);
            horse.setAdult();
            horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));

            AttributeInstance health = horse.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(definition.getHealth());
            }
            AttributeInstance speed = horse.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(definition.getSpeed());
            }
            AttributeInstance jump = horse.getAttribute(Attribute.JUMP_STRENGTH);
            if (jump != null) {
                jump.setBaseValue(definition.getJumpStrength());
            }
            horse.setHealth(definition.getHealth());

            horse.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            horse.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.name());

            OwnedHorse owned = new OwnedHorse(
                    horse.getUniqueId(),
                    player.getUniqueId(),
                    tier,
                    definition.getPrice(),
                    spawnLocation.getWorld().getName(),
                    spawnLocation.getX(),
                    spawnLocation.getY(),
                    spawnLocation.getZ(),
                    System.currentTimeMillis()
            );
            ownedHorses.put(player.getUniqueId(), owned);
            horseRepository.save(owned).exceptionally(throwable -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to persist purchased horse " + owned.getHorseUuid(), throwable);
                return null;
            });

            messages.send(player, "purchase-success",
                    Placeholder.parsed("tier", definition.getDisplayName()),
                    Placeholder.unparsed("price", String.valueOf(definition.getPrice())));
        } finally {
            // Released here rather than right after ownedHorses.put() above so a mid-spawn
            // exception still frees it up - otherwise this player could get permanently locked
            // out of ever buying again after a single failed spawn.
            pendingPurchases.remove(player.getUniqueId());
        }
    }

    public void sell(Player player) {
        OwnedHorse owned = ownedHorses.get(player.getUniqueId());
        if (owned == null) {
            messages.send(player, "no-owned-horse");
            return;
        }

        // Scheduled on the player's own current location - that's where the sale is actually
        // happening, and it's always live/correct, unlike the horse's own position which we
        // don't know yet. Using anything else here (e.g. a DB-cached "last known" spot from
        // whenever it was last dismounted) risks landing on the wrong Folia region entirely
        // if the horse has since moved, which silently breaks entity/region lookups below.
        scheduler.execute(player.getLocation(), () -> completeSale(player, owned));
    }

    private void completeSale(Player player, OwnedHorse owned) {
        Horse horse = findHorseEntity(player.getWorld(), owned.getHorseUuid());
        if (horse == null) {
            messages.send(player, "horse-not-found");
            return;
        }

        Location currentLocation = horse.getLocation();
        if (stationService.findSellStationContaining(currentLocation).isEmpty()) {
            messages.send(player, "not-at-sell-station");
            return;
        }

        double refund = owned.getCostPaid() * config.getRefundPercent();
        horse.remove();
        economy.deposit(player, refund);
        ownedHorses.remove(owned.getOwnerUuid());
        horseRepository.delete(owned.getHorseUuid()).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove sold horse " + owned.getHorseUuid() + " from storage", throwable);
            return null;
        });

        messages.send(player, "sell-success", Placeholder.unparsed("refund", String.valueOf(refund)));
    }

    private Horse findHorseEntity(World world, UUID horseUuid) {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Horse horse && entity.getUniqueId().equals(horseUuid)) {
                return horse;
            }
        }
        return null;
    }

    public boolean isTaggedHorse(Entity entity) {
        return entity instanceof Horse && entity.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
    }

    public boolean isOwner(Entity horseEntity, Player player) {
        String owner = horseEntity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return owner != null && owner.equals(player.getUniqueId().toString());
    }

    /**
     * Hands full control to the rider: cancels any in-progress "walk to owner" path, and - since
     * a sitting horse is unaware (see {@link #toggleStay}) - makes sure it's aware again so the
     * rider can actually steer it, regardless of whether it was told to stay before being mounted.
     * Called once the mount is confirmed to be the owner (see {@link HorseListener}); an
     * unauthorized mount is cancelled before this runs.
     */
    public void onMount(Horse horse) {
        horse.setAware(true);
        Pathfinder pathfinder = horse.getPathfinder();
        if (pathfinder.hasPath()) {
            pathfinder.stopPathfinding();
        }
    }

    /**
     * If told to stay, sits back down instead of resuming awareness (see {@link #toggleStay}).
     * Otherwise, kicks off an immediate follow check instead of waiting for the next periodic
     * {@link #startFollowLoop()} cycle, so the horse doesn't stand around for up to a second first.
     */
    public void onDismount(Horse horse) {
        if (isStaying(horse)) {
            horse.setAware(false);
            return;
        }
        Player owner = onlineOwner(horse);
        if (owner != null) {
            scheduler.execute(horse, () -> followOwner(horse, owner));
        }
    }

    /**
     * Every owned horse, wolf-style: walk toward the owner once they stray far enough away, and
     * stop again once close. Runs from construction rather than being tied to any single horse's
     * lifecycle, so it covers horses that already existed before a restart too - it just walks
     * whatever's currently in {@link #ownedHorses} each cycle.
     */
    private void startFollowLoop() {
        scheduler.runTaskTimer(() -> {
            for (OwnedHorse owned : ownedHorses.values()) {
                Player owner = Bukkit.getPlayer(owned.getOwnerUuid());
                if (owner == null) {
                    continue;
                }
                if (Bukkit.getEntity(owned.getHorseUuid()) instanceof Horse horse) {
                    scheduler.execute(horse, () -> followOwner(horse, owner));
                }
            }
        }, FOLLOW_PERIOD_TICKS, FOLLOW_PERIOD_TICKS);
    }

    /**
     * Toggles the owner's punch-to-command stay flag (see {@link HorseListener}) - dog/wolf-sit
     * style: {@link Horse#setAware} off, not {@link Horse#setAI}, so it still reacts to being
     * pushed/hit/knocked around like a normal mob, it just stops moving or acting on its own
     * instead of being a fully inert statue.
     */
    public void toggleStay(Horse horse, Player owner) {
        boolean nowStaying = !isStaying(horse);
        horse.getPersistentDataContainer().set(stayKey, PersistentDataType.BOOLEAN, nowStaying);

        if (nowStaying) {
            horse.setAware(false);
            // Belt-and-suspenders: setAware(false) stops new self-initiated movement, but doesn't
            // reliably guarantee an already-in-progress path gets abandoned mid-stride.
            Pathfinder pathfinder = horse.getPathfinder();
            if (pathfinder.hasPath()) {
                pathfinder.stopPathfinding();
            }
            messages.send(owner, "horse-stay-enabled");
        } else {
            horse.setAware(true);
            messages.send(owner, "horse-stay-disabled");
            // Same reasoning as onDismount(): check right away instead of leaving it standing
            // around for up to a second until the next loop cycle picks it back up.
            scheduler.execute(horse, () -> followOwner(horse, owner));
        }
    }

    private boolean isStaying(Horse horse) {
        return horse.getPersistentDataContainer().getOrDefault(stayKey, PersistentDataType.BOOLEAN, false);
    }

    private void followOwner(Horse horse, Player owner) {
        if (!horse.isValid() || !owner.isOnline() || !horse.getPassengers().isEmpty()
                || !horse.getWorld().equals(owner.getWorld()) || isStaying(horse)) {
            // Dead/removed, offline, ridden, the owner's elsewhere entirely, or told to stay put -
            // leave it alone either way. isValid()/isOnline() matter because this can run slightly
            // after being dispatched (see startFollowLoop()), by which point either could be gone.
            return;
        }

        double distance = horse.getLocation().distance(owner.getLocation());
        if (distance > FOLLOW_TELEPORT_DISTANCE) {
            // The safe-spot search reads blocks around the owner's position, so it needs to run
            // on the owner's own region, not the horse's - teleportAsync() is safe to call from
            // there and handles moving the horse across into that region itself.
            scheduler.execute(owner, () -> teleportToOwner(horse, owner));
            return;
        }

        Pathfinder pathfinder = horse.getPathfinder();
        if (distance <= FOLLOW_STOP_DISTANCE) {
            if (pathfinder.hasPath()) {
                pathfinder.stopPathfinding();
            }
        } else if (distance >= FOLLOW_START_DISTANCE || pathfinder.hasPath()) {
            pathfinder.moveTo(owner, FOLLOW_SPEED);
        }
    }

    private void teleportToOwner(Horse horse, Player owner) {
        if (!horse.isValid() || !owner.isOnline() || isStaying(horse)) {
            // The first two could have gone away between followOwner() dispatching onto the
            // owner's thread and this actually running - and so could "not staying": if the owner
            // closes the distance and toggles stay on in that same gap, this must not still yank
            // the horse to them anyway.
            return;
        }

        Location safeSpot = findSafeLocationNear(owner.getLocation());
        if (safeSpot == null) {
            // No safe spot found this attempt - the loop just tries again next cycle.
            return;
        }

        horse.teleportAsync(safeSpot).thenAccept(success -> {
            if (!success) {
                plugin.getLogger().log(Level.FINE,
                        "Follow-teleport for horse {0} to its owner was denied", horse.getUniqueId());
            }
        });
    }

    private Location findSafeLocationNear(Location center) {
        World world = center.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < TELEPORT_SEARCH_ATTEMPTS; attempt++) {
            int x = center.getBlockX() + random.nextInt(-TELEPORT_SEARCH_RADIUS, TELEPORT_SEARCH_RADIUS + 1);
            int z = center.getBlockZ() + random.nextInt(-TELEPORT_SEARCH_RADIUS, TELEPORT_SEARCH_RADIUS + 1);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location candidate = new Location(world, x + 0.5, y, z + 0.5, center.getYaw(), 0);

            // Solid, unobstructed ground isn't enough on its own - without the region check, a
            // horse could get teleported straight into someone else's claim just because the
            // owner it's chasing walked near the edge of it.
            if (world.getBlockAt(x, y - 1, z).getType().isSolid()
                    && !world.getBlockAt(x, y, z).getType().isSolid()
                    && !world.getBlockAt(x, y + 1, z).getType().isSolid()
                    && !worldGuardHook.hasAnyRegion(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Player onlineOwner(Horse horse) {
        String ownerUuid = horse.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return ownerUuid == null ? null : Bukkit.getPlayer(UUID.fromString(ownerUuid));
    }

    public void handleDeath(Horse horse) {
        String ownerUuid = horse.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (ownerUuid != null) {
            ownedHorses.remove(UUID.fromString(ownerUuid));
        }
        horseRepository.delete(horse.getUniqueId()).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove dead horse " + horse.getUniqueId() + " from storage", throwable);
            return null;
        });
    }
}
