package dev.mrsusu.pathfinding;

import dev.mrsusu.MrSusuPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Random;
import java.util.Set;

/**
 * Lightweight pathfinder that picks a random walkable block within a radius
 * from an origin, strictly avoiding water and lava when configured.
 *
 * <p>The NMS pathfinding system cannot easily be driven for fake client-side
 * entities; instead we simulate movement by sending velocity / teleport packets.
 * This class picks a reachable ground-level block and checks all blocks along
 * the straight-line path from origin to destination.</p>
 */
public final class WanderPathfinder {

    /** Liquid materials that count as "unsafe" when avoidance is enabled. */
    private static final Set<Material> LIQUID_MATERIALS = Set.of(
            Material.WATER, Material.LAVA,
            Material.BUBBLE_COLUMN      // treated as water
    );

    /** Materials the NPC must stand ON (solid ground). */
    private static final Set<Material> NON_WALKABLE_FLOOR = Set.of(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
            Material.WATER, Material.LAVA
    );

    private static final int MAX_ATTEMPTS = 20;

    private final MrSusuPlugin plugin;
    private final Random        rng = new Random();

    public WanderPathfinder(MrSusuPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------

    /**
     * Finds a safe wander destination within {@code radius} blocks of {@code origin}.
     *
     * @param origin      NPC's current spawn / home location
     * @param radius      Maximum horizontal wander distance in blocks
     * @param avoidWater  If true, destinations and path blocks containing water are rejected
     * @param avoidLava   If true, destinations and path blocks containing lava are rejected
     * @return {@link PathResult} with the target location and success flag
     */
    public PathResult findTarget(Location origin, int radius, boolean avoidWater, boolean avoidLava) {
        World world = origin.getWorld();
        if (world == null) return new PathResult(origin.clone(), false);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            // Random offset within radius (circle distribution)
            double angle  = rng.nextDouble() * 2 * Math.PI;
            double dist   = 3 + rng.nextDouble() * (radius - 3);   // min 3 blocks away
            double dx     = Math.cos(angle) * dist;
            double dz     = Math.sin(angle) * dist;

            Location candidate = origin.clone().add(dx, 0, dz);

            // Clamp Y to world height range
            candidate = findGroundAt(candidate);
            if (candidate == null) continue;

            // Check destination block itself
            if (!isSafe(candidate, avoidWater, avoidLava)) {
                plugin.debug("Candidate rejected (unsafe destination) at " + blockStr(candidate));
                continue;
            }

            // Check path from origin to destination for liquid blocks
            if (!isPathSafe(origin, candidate, avoidWater, avoidLava)) {
                plugin.debug("Candidate rejected (unsafe path) at " + blockStr(candidate));
                continue;
            }

            plugin.debug("Safe wander target found: " + blockStr(candidate));
            return new PathResult(candidate, true);
        }

        plugin.debug("No safe wander target found after " + MAX_ATTEMPTS + " attempts – NPC will idle.");
        return new PathResult(origin.clone(), false);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Walks downward from the given XZ to find the highest solid ground block.
     * Returns null if none found (e.g. inside the void).
     */
    private Location findGroundAt(Location hint) {
        World  world = hint.getWorld();
        int    x     = hint.getBlockX();
        int    z     = hint.getBlockZ();
        int    startY = Math.min(hint.getBlockY() + 5, world.getMaxHeight() - 1);

        for (int y = startY; y >= world.getMinHeight(); y--) {
            Block floor = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            Block above2 = world.getBlockAt(x, y + 2, z);

            if (!NON_WALKABLE_FLOOR.contains(floor.getType())
                    && above.getType().isAir()
                    && above2.getType().isAir()) {
                return new Location(world, x + 0.5, y + 1.0, z + 0.5, hint.getYaw(), 0f);
            }
        }
        return null;
    }

    /**
     * Returns true if the block at the given location (and the block it stands on)
     * are safe for the NPC to walk through / stand on.
     */
    private boolean isSafe(Location loc, boolean avoidWater, boolean avoidLava) {
        World world = loc.getWorld();
        Block feet  = world.getBlockAt(loc.getBlockX(), loc.getBlockY(),     loc.getBlockZ());
        Block floor = world.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());

        if (avoidWater && (isWater(feet) || isWater(floor))) return false;
        if (avoidLava  && (isLava(feet)  || isLava(floor)))  return false;
        return true;
    }

    /**
     * Checks each block along the straight horizontal path from {@code from} to {@code to}
     * using Bresenham-like stepping.
     */
    private boolean isPathSafe(Location from, Location to, boolean avoidWater, boolean avoidLava) {
        if (!avoidWater && !avoidLava) return true;   // nothing to check

        double dx     = to.getX() - from.getX();
        double dz     = to.getZ() - from.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        int    steps  = Math.max(1, (int) Math.ceil(length));
        double stepX  = dx / steps;
        double stepZ  = dz / steps;

        World world = from.getWorld();
        for (int i = 1; i <= steps; i++) {
            double px = from.getX() + stepX * i;
            double pz = from.getZ() + stepZ * i;
            // Check at feet level and one block below (the floor)
            for (int dy = 0; dy >= -1; dy--) {
                Block b = world.getBlockAt((int) Math.floor(px),
                                           from.getBlockY() + dy,
                                           (int) Math.floor(pz));
                if (avoidWater && isWater(b)) return false;
                if (avoidLava  && isLava(b))  return false;
            }
        }
        return true;
    }

    private boolean isWater(Block b) {
        return b.getType() == Material.WATER || b.getType() == Material.BUBBLE_COLUMN;
    }

    private boolean isLava(Block b) {
        return b.getType() == Material.LAVA;
    }

    private String blockStr(Location loc) {
        return "(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }
}
