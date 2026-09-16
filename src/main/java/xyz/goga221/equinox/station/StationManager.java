package xyz.goga221.equinox.station;

import xyz.goga221.equinox.data.StationDao;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class StationManager {

    private final StationDao stationDao;
    private final WorldGuardHook worldGuardHook;
    private final List<Station> stations = new CopyOnWriteArrayList<>();

    public StationManager(StationDao stationDao, WorldGuardHook worldGuardHook) {
        this.stationDao = stationDao;
        this.worldGuardHook = worldGuardHook;
        this.stations.addAll(stationDao.findAll());
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
        stationDao.insert(station);
        return true;
    }

    public boolean removeStation(String name) {
        boolean removed = stations.removeIf(existing -> existing.name().equalsIgnoreCase(name));
        if (removed) {
            stationDao.delete(name);
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
