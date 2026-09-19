package xyz.goga221.equinox.horse;

import xyz.goga221.equinox.util.Messages;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

/** Translates raw Bukkit events into {@link HorseService} calls: ride-lock, leash/breed-blocking, owner-punch-to-stay, cleanup on death. */
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

    // Mounting and attacking are guarded above, but leashing wasn't - anyone could walk off with
    // someone else's horse on a lead without ever needing to ride or damage it.
    @EventHandler(ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (!(event.getEntity() instanceof Horse horse) || !horseService.isTaggedHorse(horse)) {
            return;
        }
        if (!horseService.isOwner(horse, event.getPlayer())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "not-owner");
        }
    }

    // Everyone else's hits go through normally now - only the owner's own punch is special-cased,
    // repurposed as the stay toggle instead of dealing damage (they'd have no other free gesture
    // to command the horse with otherwise).
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerHitHorse(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player owner)
                || !(event.getEntity() instanceof Horse horse)
                || !horseService.isTaggedHorse(horse)
                || !horseService.isOwner(horse, owner)) {
            return;
        }
        event.setCancelled(true);
        horseService.toggleStay(horse, owner);
    }

    // Nothing stopped an owned horse from being fed love-mode food and bred with any other horse
    // (someone else's tagged one, or a random wild one) - the resulting foal wouldn't be tagged,
    // owned, or tracked in the database at all, just a stray horse loose in the world. Cancelling
    // the birth itself covers every way love mode could have been triggered, not just feeding.
    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        boolean involvesOwnedHorse = (event.getMother() instanceof Horse mother && horseService.isTaggedHorse(mother))
                || (event.getFather() instanceof Horse father && horseService.isTaggedHorse(father));
        if (!involvesOwnedHorse) {
            return;
        }
        event.setCancelled(true);
        if (event.getBreeder() instanceof Player breeder) {
            messages.send(breeder, "cant-breed-horse");
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Horse horse && horseService.isTaggedHorse(horse)) {
            horseService.handleDeath(horse);
        }
    }
}
