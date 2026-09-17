package xyz.goga221.equinox.station;

import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Stores every buy/sell station as its own section in a single {@code stations.yml} - admin-set
 * content that server staff can hand-edit, same as Metabasis keeps warps/groups/spawns in YAML
 * rather than a database.
 */
public final class YamlStationRepository implements StationRepository {

    private static final String ROOT_KEY = "stations";

    private final File stationsFile;
    private final TaskScheduler scheduler;
    private final Object lock = new Object();

    /**
     * Lazily loaded once, then reused for every subsequent read/write - this repository is the
     * sole writer of stations.yml, so re-parsing it from disk before every single save would be
     * wasted I/O; only the final {@code config.save(...)} actually needs to touch disk.
     */
    private YamlConfiguration config;

    public YamlStationRepository(File dataFolder, TaskScheduler scheduler) {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder: " + dataFolder);
        }
        this.stationsFile = new File(dataFolder, "stations.yml");
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Void> save(Station station) {
        return runAsync(() -> {
            YamlConfiguration cfg = configuration();
            ConfigurationSection section = cfg.createSection(ROOT_KEY + "." + station.getName());
            section.set("type", station.getType().name());
            section.set("world", station.getWorld());
            section.set("region-id", station.getRegionId());
            section.set("x", station.getX());
            section.set("y", station.getY());
            section.set("z", station.getZ());
            cfg.save(stationsFile);
            return null;
        });
    }

    @Override
    public CompletableFuture<Boolean> delete(String name) {
        return runAsync(() -> {
            YamlConfiguration cfg = configuration();
            ConfigurationSection section = cfg.getConfigurationSection(ROOT_KEY);
            boolean existed = section != null && section.isConfigurationSection(name);
            if (existed) {
                section.set(name, null);
                cfg.save(stationsFile);
            }
            return existed;
        });
    }

    @Override
    public CompletableFuture<List<Station>> loadAll() {
        return runAsync(() -> {
            List<Station> stations = new ArrayList<>();
            YamlConfiguration cfg = configuration();
            ConfigurationSection section = cfg.getConfigurationSection(ROOT_KEY);
            if (section != null) {
                for (String name : section.getKeys(false)) {
                    ConfigurationSection stationSection = section.getConfigurationSection(name);
                    if (stationSection == null) {
                        continue;
                    }
                    stations.add(new Station(
                            name,
                            StationType.valueOf(stationSection.getString("type")),
                            stationSection.getString("world"),
                            stationSection.getString("region-id"),
                            stationSection.getDouble("x"),
                            stationSection.getDouble("y"),
                            stationSection.getDouble("z")
                    ));
                }
            }
            return stations;
        });
    }

    /** Called only from within {@link #lock}, so lazy init needs no extra synchronization. */
    private YamlConfiguration configuration() {
        if (config == null) {
            config = YamlConfiguration.loadConfiguration(stationsFile);
        }
        return config;
    }

    private <T> CompletableFuture<T> runAsync(IOAction<T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.runTaskAsynchronously(() -> {
            synchronized (lock) {
                try {
                    future.complete(action.run());
                } catch (IOException e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @FunctionalInterface
    private interface IOAction<T> {
        T run() throws IOException;
    }
}
