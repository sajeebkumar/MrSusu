package dev.mrsusu.npc;

import org.bukkit.Location;

/**
 * Immutable snapshot of the NPC's current logical state.
 *
 * @param entityId      Network entity ID used in all client-bound packets
 * @param uuid          UUID of the fake player profile
 * @param homeLocation  The original spawn point (wander radius origin)
 * @param currentLocation The NPC's current interpolated world position
 * @param spawned       Whether the NPC is currently visible to clients
 */
public record NpcState(
        int       entityId,
        java.util.UUID uuid,
        Location  homeLocation,
        Location  currentLocation,
        boolean   spawned
) {
    public NpcState withLocation(Location loc) {
        return new NpcState(entityId, uuid, homeLocation, loc, spawned);
    }

    public NpcState withSpawned(boolean value) {
        return new NpcState(entityId, uuid, homeLocation, currentLocation, value);
    }
}
