package xyz.goga221.equinox.station;

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
    public CompletableFuture<Integer> loadStationsIntoCache() {
        return stationRepository.loadAll().thenApply(loaded -> {
            stations.clear();
            stations.addAll(loaded);
            return stations.size();
        });
    }

    /**
     * Registers an existing WorldGuard region (in {@code world}) as a station named
     * {@code name}. The station's spawn/lookup point is the region's bounding-box center.
     *
     * @return a future resolving to false if the region doesn't exist in that world.
     */
    public CompletableFuture<Boolean> createStation(String name, StationType type, World world, String regionId) {
        Optional<Location> center = worldGuardHook.computeCenter(world, regionId);
        if (center.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        Location location = center.get();
        Station station = new Station(name, type, world.getName(), regionId, location.getX(), location.getY(), location.getZ());
        stations.removeIf(existing -> existing.getName().equalsIgnoreCase(name));
        stations.add(station);
        return stationRepository.save(station).thenApply(unused -> true);
    }

    /** @return a future resolving to false if no station by that name existed. */
    public CompletableFuture<Boolean> removeStation(String name) {
        boolean removed = stations.removeIf(existing -> existing.getName().equalsIgnoreCase(name));
        if (!removed) {
            return CompletableFuture.completedFuture(false);
        }
        return stationRepository.delete(name);
    }

    public List<Station> list() {
        return List.copyOf(stations);
    }

    public Optional<Station> findNearestBuyStation(Location location) {
        return stations.stream()
                .filter(station -> station.getType() == StationType.BUY)
                .filter(station -> location.getWorld() != null && location.getWorld().getName().equals(station.getWorld()))
                .min(Comparator.comparingDouble(station -> station.distanceSquared(location)));
    }

    public Optional<Station> findSellStationContaining(Location horseLocation) {
        return stations.stream()
                .filter(station -> station.getType() == StationType.SELL)
                .filter(station -> horseLocation.getWorld() != null && horseLocation.getWorld().getName().equals(station.getWorld()))
                .filter(station -> worldGuardHook.contains(horseLocation, station.getRegionId()))
                .findFirst();
    }
}
