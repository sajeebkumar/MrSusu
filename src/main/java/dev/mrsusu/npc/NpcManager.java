package dev.mrsusu.npc;

import dev.mrsusu.MrSusuPlugin;
import dev.mrsusu.pathfinding.PathResult;
import dev.mrsusu.pathfinding.WanderPathfinder;
import dev.mrsusu.skin.SkinData;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central manager responsible for:
 * <ul>
 *   <li>Spawning / despawning the fake-player NPC via packets</li>
 *   <li>Sending NPC packets to newly-joined players</li>
 *   <li>Running the movement / head-rotation loop</li>
 * </ul>
 */
public final class NpcManager {

    // -------------------------------------------------------------------------
    // Static entity-id counter (never re-used across server lifetime)
    // -------------------------------------------------------------------------
    private static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(Integer.MAX_VALUE - 1024);

    private final MrSusuPlugin    plugin;
    private final WanderPathfinder pathfinder;

    /** Mutable NPC state; null when the NPC is not spawned. */
    private volatile NpcState state;

    /** Cached skin applied at spawn time. */
    private SkinData currentSkin = SkinData.FALLBACK;

    /** Movement / head-rotation task. */
    private BukkitTask movementTask;

    /** Smooth-movement interpolation task. */
    private BukkitTask interpolationTask;

    /** Interpolation state */
    private Location interpolationTarget;
    private Location interpolationCurrent;
    private int      interpolationSteps;
    private int      interpolationTick;

    public NpcManager(MrSusuPlugin plugin) {
        this.plugin     = plugin;
        this.pathfinder = new WanderPathfinder(plugin);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Spawns the NPC at {@code location} for all online players.
     * Fetches the skin asynchronously before spawning.
     */
    public void spawn(Location location) {
        if (state != null && state.spawned()) {
            plugin.getLogger().info("[MrSusu] NPC is already spawned – despawning first.");
            despawn();
        }

        plugin.getSkinFetcher().fetchSkin(skin -> {
            currentSkin = skin;
            int       entityId = ENTITY_ID_COUNTER.getAndDecrement();
            UUID      uuid     = UUID.randomUUID();
            NpcState  newState = new NpcState(entityId, uuid,
                    location.clone(), location.clone(), true);
            state = newState;

            for (Player player : Bukkit.getOnlinePlayers()) {
                sendSpawnPackets(player, newState, skin);
            }

            startMovementLoop();
            plugin.getLogger().info("[MrSusu] NPC spawned at " + fmtLoc(location));
        });
    }

    /** Spawns from the saved DataManager location. */
    public void spawnFromSaved() {
        Location loc = plugin.getDataManager().loadLocation();
        if (loc == null) {
            plugin.getLogger().warning("[MrSusu] Could not load saved spawn location.");
            return;
        }
        spawn(loc);
    }

    /**
     * Despawns the NPC and removes it from all online players' clients.
     */
    public void despawn() {
        if (state == null || !state.spawned()) return;

        stopMovementLoop();

        NpcState s = state;
        state = null;

        PacketHelper.broadcast(PacketHelper.removePacket(s.entityId()));
        // Ensure tab-list cleanup for older clients
        PacketHelper.broadcast(new ClientboundPlayerInfoRemovePacket(List.of(s.uuid())));

        plugin.getLogger().info("[MrSusu] NPC despawned.");
    }

    /**
     * Sends all necessary spawn packets to a single player (used on join).
     */
    public void sendSpawnPacketsToPlayer(Player player) {
        NpcState s = state;
        if (s == null || !s.spawned()) return;
        sendSpawnPackets(player, s, currentSkin);
    }

    /** Returns true if the NPC is currently spawned. */
    public boolean isSpawned() {
        return state != null && state.spawned();
    }

    /** Returns the entity ID of the active NPC, or -1. */
    public int getEntityId() {
        return state != null ? state.entityId() : -1;
    }

    /** Returns the UUID of the active NPC, or null. */
    public UUID getNpcUuid() {
        return state != null ? state.uuid() : null;
    }

    /** Returns a copy of the current NPC location, or null. */
    public Location getCurrentLocation() {
        return state != null ? state.currentLocation().clone() : null;
    }

    // =========================================================================
    // Packet dispatch
    // =========================================================================

    private void sendSpawnPackets(Player player, NpcState s, SkinData skin) {
        com.mojang.authlib.GameProfile profile = PacketHelper.buildProfile(s.uuid(), skin);

        // 1. Add to tab-list (mandatory before spawn)
        PacketHelper.send(player, PacketHelper.addToTabList(profile));

        // 2. Spawn entity
        PacketHelper.send(player, PacketHelper.spawnPacket(s));

        // 3. Metadata
        PacketHelper.send(player, PacketHelper.metaPacket(s.entityId()));

        // 4. Remove from tab-list after 2 ticks (NPC should not show in tab)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                PacketHelper.send(player, PacketHelper.removeFromTabList(s.uuid()));
            }
        }, 2L);
    }

    // =========================================================================
    // Movement loop
    // =========================================================================

    private void startMovementLoop() {
        stopMovementLoop(); // safety

        int intervalTicks = plugin.getConfig().getInt("movement.interval-seconds", 10) * 20;

        movementTask = Bukkit.getScheduler().runTaskTimer(plugin, this::onMovementTick,
                intervalTicks, intervalTicks);

        // Smooth interpolation – runs every tick
        interpolationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::onInterpolationTick, 1L, 1L);
    }

    private void stopMovementLoop() {
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }
        if (interpolationTask != null) {
            interpolationTask.cancel();
            interpolationTask = null;
        }
        interpolationTarget  = null;
        interpolationCurrent = null;
        interpolationSteps   = 0;
        interpolationTick    = 0;
    }

    /**
     * Called every {@code interval-seconds}:
     * 70% – pick a wander target; 30% – idle and track nearest player head.
     */
    private void onMovementTick() {
        NpcState s = state;
        if (s == null || !s.spawned()) return;

        if (Math.random() < 0.70) {
            doWander(s);
        } else {
            doIdleHeadTracking(s);
        }
    }

    private void doWander(NpcState s) {
        int     radius      = plugin.getConfig().getInt("movement.radius", 15);
        boolean avoidWater  = plugin.getConfig().getBoolean("movement.avoid-water", true);
        boolean avoidLava   = plugin.getConfig().getBoolean("movement.avoid-lava", true);

        PathResult result = pathfinder.findTarget(s.homeLocation(), radius, avoidWater, avoidLava);
        if (!result.found()) {
            plugin.debug("Wander: no safe target – idling.");
            doIdleHeadTracking(s);
            return;
        }

        Location target = result.destination();
        plugin.debug("Wander target: " + fmtLoc(target));

        // Face the walk direction
        Location from = s.currentLocation();
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        float  yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        target.setYaw(yaw);
        target.setPitch(0f);

        // Begin smooth interpolation
        interpolationTarget  = target.clone();
        interpolationCurrent = from.clone();
        // Steps = ticks until next interval (smooth movement across full interval)
        interpolationSteps = plugin.getConfig().getInt("movement.interval-seconds", 10) * 20;
        interpolationTick  = 0;

        state = s.withLocation(target);
    }

    private void doIdleHeadTracking(NpcState s) {
        Player nearest = nearestPlayerWithin(s.currentLocation(), 8.0);
        if (nearest == null) return;

        Location npcLoc    = s.currentLocation();
        Location playerLoc = nearest.getLocation();

        // Yaw toward the player
        double dx  = playerLoc.getX() - npcLoc.getX();
        double dz  = playerLoc.getZ() - npcLoc.getZ();
        float  yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        PacketHelper.broadcast(PacketHelper.headRotationPacket(s.entityId(), yaw));
        plugin.debug("Head-tracking player " + nearest.getName() + " yaw=" + yaw);
    }

    /**
     * Interpolation tick: smoothly moves the NPC from current → target
     * by sending a teleport packet every N ticks.
     */
    private void onInterpolationTick() {
        if (interpolationTarget == null || interpolationCurrent == null) return;
        if (interpolationTick >= interpolationSteps) {
            interpolationTarget  = null;
            interpolationCurrent = null;
            return;
        }

        interpolationTick++;

        double t   = (double) interpolationTick / interpolationSteps;
        double x   = lerp(interpolationCurrent.getX(), interpolationTarget.getX(), t);
        double y   = lerp(interpolationCurrent.getY(), interpolationTarget.getY(), t);
        double z   = lerp(interpolationCurrent.getZ(), interpolationTarget.getZ(), t);
        float  yaw = interpolationTarget.getYaw();

        NpcState s = state;
        if (s == null) return;

        Location mid = new Location(interpolationTarget.getWorld(), x, y, z, yaw, 0f);
        state = s.withLocation(mid);

        // Send a teleport packet every 5 ticks (not every tick, to reduce bandwidth)
        if (interpolationTick % 5 == 0 || interpolationTick == 1) {
            NpcState updated = state;
            PacketHelper.broadcast(PacketHelper.teleportPacket(updated));
            PacketHelper.broadcast(PacketHelper.headRotationPacket(updated.entityId(), yaw));
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private Player nearestPlayerWithin(Location center, double radius) {
        Player nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : center.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(center);
            if (d < radius * radius && d < bestDist) {
                bestDist = d;
                nearest  = p;
            }
        }
        return nearest;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static String fmtLoc(Location l) {
        return String.format("(%.1f, %.1f, %.1f) in %s",
                l.getX(), l.getY(), l.getZ(), l.getWorld().getName());
    }
}
