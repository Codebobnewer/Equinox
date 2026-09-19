package xyz.goga221.equinox.station;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
     * <p>Names are normalized to lowercase before touching the cache or the repository - station
     * names used to be compared case-insensitively in memory but saved/deleted case-sensitively in
     * the YAML file, so e.g. "Foo" then "foo" looked like the same station in-game but left an
     * orphaned "Foo" section on disk that silently came back as a duplicate on the next restart.
     * Normalizing once here keeps the cache and the file using the exact same key.
     *
     * <p>Names are a single namespace shared by both station types - {@link #removeStation} only
     * takes a name, with no type to disambiguate, so a buy and a sell station can't coexist under
     * the same name. Reusing a name already held by a station of the *other* type is rejected
     * rather than silently deleting it, since that used to happen without any warning.
     */
    public CompletableFuture<CreateStationResult> createStation(String name, StationType type, World world, String regionId) {
        String normalizedName = name.toLowerCase(Locale.ROOT);

        Optional<Station> existing = stations.stream()
                .filter(station -> station.getName().equals(normalizedName))
                .findFirst();
        if (existing.isPresent() && existing.get().getType() != type) {
            return CompletableFuture.completedFuture(CreateStationResult.NAME_TAKEN_BY_OTHER_TYPE);
        }

        Optional<Location> center = worldGuardHook.computeCenter(world, regionId);
        if (center.isEmpty()) {
            return CompletableFuture.completedFuture(CreateStationResult.REGION_NOT_FOUND);
        }
        Location location = center.get();
        Station station = new Station(normalizedName, type, world.getName(), regionId, location.getX(), location.getY(), location.getZ());
        stations.removeIf(other -> other.getName().equals(normalizedName));
        stations.add(station);
        // If the save fails, undo the optimistic cache add so the cache doesn't drift from the
        // file it's supposed to mirror - whenComplete only observes the outcome, it doesn't
        // swallow the exception, so the caller's own .exceptionally() still sees the failure.
        return stationRepository.save(station)
                .whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        stations.remove(station);
                    }
                })
                .thenApply(unused -> CreateStationResult.CREATED);
    }

    /** @return a future resolving to false if no station by that name existed. */
    public CompletableFuture<Boolean> removeStation(String name) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        Optional<Station> removed = stations.stream()
                .filter(existing -> existing.getName().equals(normalizedName))
                .findFirst();
        if (removed.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        stations.remove(removed.get());
        // Same reasoning as createStation(): restore the cache entry if the delete didn't
        // actually happen, instead of leaving the station gone in-game but still on disk.
        return stationRepository.delete(normalizedName)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        stations.add(removed.get());
                    }
                });
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
