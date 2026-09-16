package xyz.goga221.equinox.station;

import xyz.goga221.equinox.data.StationRepository;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/** Owns the in-memory station cache: admin create/remove, and the buy/sell lookups HorseService needs. */
public final class StationService {

    private final StationRepository stationRepository;
    private final WorldGuardHook worldGuardHook;
    private final List<Station> stations = new CopyOnWriteArrayList<>();

    public StationService(StationRepository stationRepository, WorldGuardHook worldGuardHook) {
        this.stationRepository = stationRepository;
        this.worldGuardHook = worldGuardHook;
    }

    /** Loads every station into the cache off the main thread, returning how many were loaded. */
    public CompletableFuture<Integer> loadStationsIntoCache(TaskScheduler scheduler) {
        return stationRepository.findAllAsync(scheduler).thenApply(loaded -> {
            stations.clear();
            stations.addAll(loaded);
            return stations.size();
        });
    }

    /**
     * Registers an existing WorldGuard region (in {@code world}) as a station named
     * {@code name}. The station's spawn/lookup point is the region's bounding-box center.
     *
     * @return false if the region doesn't exist in that world.
     */
    public boolean createStation(String name, StationType type, World world, String regionId) {
        Optional<Location> center = worldGuardHook.computeCenter(world, regionId);
        if (center.isEmpty()) {
            return false;
        }
        Location location = center.get();
        Station station = new Station(name, type, world.getName(), regionId, location.getX(), location.getY(), location.getZ());
        stations.removeIf(existing -> existing.name().equalsIgnoreCase(name));
        stations.add(station);
        stationRepository.insert(station);
        return true;
    }

    public boolean removeStation(String name) {
        boolean removed = stations.removeIf(existing -> existing.name().equalsIgnoreCase(name));
        if (removed) {
            stationRepository.delete(name);
        }
        return removed;
    }

    public List<Station> list() {
        return List.copyOf(stations);
    }

    public Optional<Station> findNearestBuyStation(Location location) {
        return stations.stream()
                .filter(station -> station.type() == StationType.BUY)
                .filter(station -> location.getWorld() != null && location.getWorld().getName().equals(station.world()))
                .min(Comparator.comparingDouble(station -> station.distanceSquared(location)));
    }

    public Optional<Station> findSellStationContaining(Location horseLocation) {
        return stations.stream()
                .filter(station -> station.type() == StationType.SELL)
                .filter(station -> horseLocation.getWorld() != null && horseLocation.getWorld().getName().equals(station.world()))
                .filter(station -> worldGuardHook.contains(horseLocation, station.regionId()))
                .findFirst();
    }
}
