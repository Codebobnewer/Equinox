package xyz.goga221.equinox.horse;

import xyz.goga221.equinox.util.Messages;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

/** Translates raw Bukkit events into {@link HorseService} calls: ride-lock, hit-blocking, cleanup on death. */
@RequiredArgsConstructor
public final class HorseListener implements Listener {

    private final HorseService horseService;
    private final Messages messages;

    // ignoreCancelled: if some other plugin (region protection, etc.) already blocked this mount,
    // we must not still act as if it succeeded - that desyncs pathfinder state from what actually
    // happened, since the player never actually got on.
    @EventHandler(ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Horse horse) || !horseService.isTaggedHorse(horse)) {
            return;
        }
        if (!(event.getEntered() instanceof Player player)) {
            return;
        }
        if (!horseService.isOwner(horse, player)) {
            event.setCancelled(true);
            messages.send(player, "not-owner");
            return;
        }
        horseService.onMount(horse);
    }

    // Same reasoning as onVehicleEnter: a cancelled exit means the player is still riding, so
    // starting the follow behaviour here would fight their control of the horse mid-ride.
    @EventHandler(ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getVehicle() instanceof Horse horse && horseService.isTaggedHorse(horse)) {
            horseService.onDismount(horse);
        }
    }

    // Nobody can actually damage a tagged horse, so the owner's own punch is free to repurpose:
    // a direct melee hit (not a projectile) from the owner toggles stay instead of doing nothing.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerHitHorse(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Horse horse) || !horseService.isTaggedHorse(horse)) {
            return;
        }
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null) {
            return;
        }
        event.setCancelled(true);

        if (event.getDamager() instanceof Player owner && horseService.isOwner(horse, owner)) {
            horseService.toggleStay(horse, owner);
        } else {
            messages.send(attacker, "cant-hit-horse");
        }
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Horse horse && horseService.isTaggedHorse(horse)) {
            horseService.handleDeath(horse);
        }
    }
}
