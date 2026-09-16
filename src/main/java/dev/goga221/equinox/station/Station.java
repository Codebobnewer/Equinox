package dev.goga221.equinox.station;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record Station(
        String name,
        StationType type,
        String world,
        String regionId,
        double x,
        double y,
        double z
) {

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
