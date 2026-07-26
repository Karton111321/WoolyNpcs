package ru.qweyns.woolynpcs.waypoint;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class Terrain {
    private Terrain() {}

    private static int maxStepUp() {
        return ru.qweyns.woolynpcs.WoolyNpcs.getInstance().getConfigManager().getTerrainStepUp();
    }

    private static int maxDrop() {
        return ru.qweyns.woolynpcs.WoolyNpcs.getInstance().getConfigManager().getTerrainMaxDrop();
    }

    public static Location snap(Location target) {
        World world = target.getWorld();
        if (world == null) return target;

        Location result = target.clone();

        int climbed = 0;
        while (climbed <= maxStepUp() && isBlocked(world, result)) {
            result.add(0, 1, 0);
            climbed++;
        }
        if (isBlocked(world, result)) return null;

        if (isPassable(world, result.clone().subtract(0, 1, 0))) {
            Location probe = result.clone();
            int dropped = 0;
            while (dropped < maxDrop() && isPassable(world, probe.clone().subtract(0, 1, 0))) {
                probe.subtract(0, 1, 0);
                dropped++;
            }
            if (!isPassable(world, probe.clone().subtract(0, 1, 0))) {
                result = probe;
            }
        }

        return result;
    }

    private static boolean isBlocked(World world, Location loc) {
        return !isPassable(world, loc) || !isPassable(world, loc.clone().add(0, 1, 0));
    }

    private static boolean isPassable(World world, Location loc) {
        if (loc.getY() < world.getMinHeight()) return false;
        Block block = world.getBlockAt(loc);
        return block == null || block.isPassable();
    }
}
