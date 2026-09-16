package dev.goga221.equinox.horse;

import dev.goga221.equinox.util.Messages;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

public final class HorseListener implements Listener {

    private final HorseManager horseManager;
    private final Messages messages;

    public HorseListener(HorseManager horseManager, Messages messages) {
        this.horseManager = horseManager;
        this.messages = messages;
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Horse horse) || !horseManager.isTaggedHorse(horse)) {
            return;
        }
        if (!(event.getEntered() instanceof Player player)) {
            return;
        }
        if (!horseManager.isOwner(horse, player)) {
            event.setCancelled(true);
            messages.send(player, "not-owner");
            return;
        }
        horseManager.onMount(horse);
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getVehicle() instanceof Horse horse && horseManager.isTaggedHorse(horse)) {
            horseManager.onDismount(horse);
        }
    }

    // LOWEST so this cancellation happens before onHorseDamaged() below sees the event - both
    // handlers share EntityDamageEvent's handler list since EntityDamageByEntityEvent doesn't
    // declare its own, and ignoreCancelled there means a blocked hit never triggers a panic.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerHitHorse(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Horse horse) || !horseManager.isTaggedHorse(horse)) {
            return;
        }
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker != null) {
            event.setCancelled(true);
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

    // ignoreCancelled: this event fires for every point of damage to every entity on the whole
    // server, so skipping cancelled ones before our handler even runs (rather than checking
    // isCancelled() ourselves) avoids paying for a method dispatch on hits that didn't land.
    @EventHandler(ignoreCancelled = true)
    public void onHorseDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Horse horse && horseManager.isTaggedHorse(horse)) {
            horseManager.onAttacked(horse);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Horse horse && horseManager.isTaggedHorse(horse)) {
            horseManager.handleDeath(horse);
        }
    }
}
