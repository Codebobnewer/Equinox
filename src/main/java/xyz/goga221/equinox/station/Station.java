package xyz.goga221.equinox.station;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** A registered buy/sell station: the WorldGuard region it's backed by, plus that region's cached center point. */
@Getter
@With
@AllArgsConstructor
public class Station {

    private final String name;
    private final StationType type;
    private final String world;
    private final String regionId;
    private final double x;
    private final double y;
    private final double z;

    public Location center() {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            return null;
        }
        return new Location(bukkitWorld, x, y, z);
    }

    public double distanceSquared(Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(world)) {
            return Double.MAX_VALUE;
        }
        double dx = location.getX() - x;
        double dy = location.getY() - y;
        double dz = location.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
