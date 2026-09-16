package xyz.goga221.equinox.horse;

import xyz.goga221.equinox.config.ConfigManager;
import xyz.goga221.equinox.config.TierDefinition;
import xyz.goga221.equinox.data.HorseDao;
import xyz.goga221.equinox.economy.EconomyProvider;
import xyz.goga221.equinox.station.Station;
import xyz.goga221.equinox.station.StationManager;
import xyz.goga221.equinox.util.Messages;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the whole purchase/sell lifecycle: ownership checks, spending/refunding through
 * {@link EconomyProvider}, spawning/removing the actual {@link Horse} entity, and tagging
 * it so {@link HorseListener} can enforce exclusive riding and clean up on death.
 */
public final class HorseManager {

    private final Plugin plugin;
    private final ConfigManager config;
    private final HorseDao horseDao;
    private final StationManager stationManager;
    private final EconomyProvider economy;
    private final TaskScheduler scheduler;
    private final Messages messages;

    private final NamespacedKey ownerKey;
    private final NamespacedKey tierKey;

    // Tracks the timestamp of each horse's most recent hit while panicking, purely in-memory,
    // so a repeated hit can restart the panic timer instead of a stale delayed task cutting it short.
    private final Map<UUID, Long> lastHitAt = new ConcurrentHashMap<>();

    // Ownership is checked constantly (every purchase, sell and /stable open) but changes rarely,
    // so it's kept in memory - keyed by owner UUID - instead of hitting SQLite on those hot paths.
    // Seeded once from the database at startup; every purchase/sell/death keeps it in sync.
    private final Map<UUID, OwnedHorse> ownedHorses = new ConcurrentHashMap<>();

    public HorseManager(Plugin plugin, ConfigManager config, HorseDao horseDao, StationManager stationManager,
                         EconomyProvider economy, TaskScheduler scheduler, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.horseDao = horseDao;
        this.stationManager = stationManager;
        this.economy = economy;
        this.scheduler = scheduler;
        this.messages = messages;
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.tierKey = new NamespacedKey(plugin, "tier");
        for (OwnedHorse owned : horseDao.findAll()) {
            ownedHorses.put(owned.ownerUuid(), owned);
        }
    }

    public boolean hasHorse(UUID playerUuid) {
        return ownedHorses.containsKey(playerUuid);
    }

    public void purchase(Player player, HorseTier tier) {
        if (ownedHorses.containsKey(player.getUniqueId())) {
            messages.send(player, "already-own-horse");
            return;
        }

        TierDefinition definition = config.getTier(tier);
        if (definition == null) {
            return;
        }

        Optional<Station> station = stationManager.findNearestBuyStation(player.getLocation());
        if (station.isEmpty()) {
            messages.send(player, "no-buy-station");
            return;
        }

        if (!economy.has(player, definition.price())) {
            messages.send(player, "not-enough-money");
            return;
        }

        Location spawnLocation = station.get().center();
        economy.withdraw(player, definition.price());
        scheduler.execute(spawnLocation, () -> spawnPurchasedHorse(player, tier, definition, spawnLocation));
    }

    private void spawnPurchasedHorse(Player player, HorseTier tier, TierDefinition definition, Location spawnLocation) {
        Horse horse = (Horse) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.HORSE);
        horse.setColor(definition.color());
        horse.setStyle(definition.style());
        horse.setTamed(true);
        horse.setOwner(player);
        horse.setAdult();
        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        // Stands still until someone actually rides it; disabling AI stops it wandering off,
        // but knockback from being attacked still applies since that's physics, not AI.
        horse.setAI(false);

        AttributeInstance health = horse.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(definition.health());
        }
        AttributeInstance speed = horse.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(definition.speed());
        }
        AttributeInstance jump = horse.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) {
            jump.setBaseValue(definition.jumpStrength());
        }
        horse.setHealth(definition.health());

        horse.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        horse.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.name());

        OwnedHorse owned = new OwnedHorse(
                horse.getUniqueId(),
                player.getUniqueId(),
                tier,
                definition.price(),
                spawnLocation.getWorld().getName(),
                spawnLocation.getX(),
                spawnLocation.getY(),
                spawnLocation.getZ(),
                System.currentTimeMillis()
        );
        ownedHorses.put(player.getUniqueId(), owned);
        scheduler.runTaskAsynchronously(() -> horseDao.insert(owned));

        messages.send(player, "purchase-success",
                Placeholder.parsed("tier", definition.displayName()),
                Placeholder.unparsed("price", String.valueOf(definition.price())));
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
        Horse horse = findHorseEntity(player.getWorld(), owned.horseUuid());
        if (horse == null) {
            messages.send(player, "horse-not-found");
            return;
        }

        Location currentLocation = horse.getLocation();
        if (stationManager.findSellStationContaining(currentLocation).isEmpty()) {
            messages.send(player, "not-at-sell-station");
            return;
        }

        double refund = owned.costPaid() * config.getRefundPercent();
        horse.remove();
        economy.deposit(player, refund);
        ownedHorses.remove(owned.ownerUuid());
        scheduler.runTaskAsynchronously(() -> horseDao.delete(owned.horseUuid()));

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
     * Re-enables AI so the owner can actually ride it. Called once the mount is confirmed to be
     * the owner (see {@link HorseListener}) - an unauthorized mount is cancelled before this runs.
     */
    public void onMount(Horse horse) {
        horse.setAI(true);
    }

    /**
     * Stops the horse wandering off once it's riderless again. AI off doesn't stop it reacting to
     * being attacked - knockback from damage is physics, not AI - it just stops idle wandering.
     */
    public void onDismount(Horse horse) {
        horse.setAI(false);
    }

    /**
     * A riderless horse normally has AI off (see {@link #onDismount}). Getting hit re-enables AI
     * so it can panic/flee like a real mob, then switches it back off after the configured
     * duration - unless it gets hit again first, which pushes the timer back out.
     */
    public void onAttacked(Horse horse) {
        if (!horse.getPassengers().isEmpty()) {
            // Already being ridden, AI is already on for the rider's own control - nothing to do,
            // and onDismount() will turn it back off once they actually get off.
            return;
        }

        horse.setAI(true);

        UUID horseUuid = horse.getUniqueId();
        long hitAt = System.currentTimeMillis();
        lastHitAt.put(horseUuid, hitAt);

        scheduler.runTaskLater(horse, () -> {
            Long mostRecentHit = lastHitAt.get(horseUuid);
            boolean hitAgainSince = mostRecentHit == null || mostRecentHit != hitAt;
            if (hitAgainSince || !horse.isValid() || !horse.getPassengers().isEmpty()) {
                return;
            }
            lastHitAt.remove(horseUuid);
            horse.setAI(false);
        }, config.getHorsePanicDurationTicks());
    }

    public void handleDeath(Horse horse) {
        lastHitAt.remove(horse.getUniqueId());
        String ownerUuid = horse.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (ownerUuid != null) {
            ownedHorses.remove(UUID.fromString(ownerUuid));
        }
        scheduler.runTaskAsynchronously(() -> horseDao.delete(horse.getUniqueId()));
    }
}
