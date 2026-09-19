package xyz.goga221.equinox.station;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;

/**
 * Thin wrapper over the WorldGuard region API. Buy/sell stations are anchored to
 * WorldGuard regions that admins already created with the WorldGuard/WorldEdit commands;
 * this plugin only reads region bounds and membership, it never creates or edits regions.
 */
public final class WorldGuardHook {

    public Optional<ProtectedRegion> getRegion(World world, String regionId) {
        RegionManager manager = WorldGuard.getInstance().getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (manager == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(manager.getRegion(regionId));
    }

    /**
     * Midpoint of the region's bounding box on X/Z, snapped to the highest solid block
     * (plus one) so a spawned horse doesn't appear inside terrain.
     */
    public Optional<Location> computeCenter(World world, String regionId) {
        return getRegion(world, regionId).map(region -> {
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            double midX = (min.x() + max.x() + 1) / 2.0;
            double midZ = (min.z() + max.z() + 1) / 2.0;
            int y = world.getHighestBlockYAt((int) midX, (int) midZ) + 1;
            return new Location(world, midX, y, midZ);
        });
    }

    public boolean contains(Location location, String regionId) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        return getRegion(world, regionId)
                .map(region -> region.contains(BukkitAdapter.asBlockVector(location)))
                .orElse(false);
    }

    /**
     * Whether any WorldGuard region at all covers this point, ignoring the implicit
     * {@code __global__} region every world has by default. Used to keep the follow-teleport
     * (see HorseService) from dropping a horse into someone else's claim just because it
     * happened to be solid, unobstructed ground.
     */
    public boolean hasAnyRegion(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        RegionManager manager = WorldGuard.getInstance().getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (manager == null) {
            return false;
        }
        for (ProtectedRegion region : manager.getApplicableRegions(BukkitAdapter.asBlockVector(location))) {
            if (!region.getId().equals(ProtectedRegion.GLOBAL_REGION)) {
                return true;
            }
        }
        return false;
    }
}
