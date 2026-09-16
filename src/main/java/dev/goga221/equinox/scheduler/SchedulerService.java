package dev.goga221.equinox.scheduler;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Thin wrapper around UniversalScheduler. Every entity spawn / world mutation in this
 * plugin should go through here instead of calling {@code Bukkit.getScheduler()} or
 * {@code World.spawn()} directly, since callback threads (e.g. an InvUI click handler)
 * aren't guaranteed to already be on the correct region thread under Folia.
 */
public final class SchedulerService {

    private final TaskScheduler scheduler;

    public SchedulerService(Plugin plugin) {
        this.scheduler = UniversalScheduler.getScheduler(plugin);
    }

    public void runAt(Location location, Runnable task) {
        scheduler.execute(location, task);
    }

    public void runAtEntity(Entity entity, Runnable task) {
        scheduler.execute(entity, task);
    }

    public void runLaterAtEntity(Entity entity, Runnable task, long delayTicks) {
        scheduler.runTaskLater(entity, task, delayTicks);
    }

    public void runGlobal(Runnable task) {
        scheduler.execute(task);
    }

    public void runAsync(Runnable task) {
        scheduler.runTaskAsynchronously(task);
    }
}
