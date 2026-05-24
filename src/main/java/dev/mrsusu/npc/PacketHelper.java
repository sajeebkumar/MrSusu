package dev.mrsusu.npc;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.mrsusu.skin.SkinData;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * All client-bound packet construction for Mr Susu fake-player NPC.
 *
 * <p>Targets Paper 1.21.1 – 1.21.4 with Mojang mappings.
 * All class/field names use the de-obfuscated Mojang-mapped identifiers.</p>
 */
public final class PacketHelper {

    private PacketHelper() {}

    // -------------------------------------------------------------------------
    // Profile & tab-list
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link GameProfile} with the given skin applied.
     * The display name is always "Mr Susu".
     */
    public static GameProfile buildProfile(UUID uuid, SkinData skin) {
        GameProfile profile = new GameProfile(uuid, "Mr Susu");
        if (skin.isValid()) {
            profile.getProperties().put("textures",
                    new Property("textures", skin.value(), skin.signature()));
        }
        return profile;
    }

    /**
     * Returns the {@link ClientboundPlayerInfoUpdatePacket} that adds
     * the fake player to the tab-list (required before spawning).
     */
    public static ClientboundPlayerInfoUpdatePacket addToTabList(GameProfile profile) {
        return ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(
                List.of(buildDummyServerPlayer(profile))
        );
    }

    /**
     * Returns the packet that removes the fake player from the tab-list.
     * Should be sent ~2 ticks after spawn so the skin has already been applied.
     */
    public static ClientboundPlayerInfoRemovePacket removeFromTabList(UUID uuid) {
        return new ClientboundPlayerInfoRemovePacket(List.of(uuid));
    }

    // -------------------------------------------------------------------------
    // Spawn & remove
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link ClientboundAddEntityPacket} that spawns the NPC
     * as a player entity at the given location.
     *
     * <p>In 1.21 the {@code ClientboundAddPlayerPacket} is merged into
     * {@code ClientboundAddEntityPacket} with {@link EntityType#PLAYER}.</p>
     */
    public static ClientboundAddEntityPacket spawnPacket(NpcState state) {
        Location loc = state.currentLocation();
        return new ClientboundAddEntityPacket(
                state.entityId(),
                state.uuid(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getPitch(),
                loc.getYaw(),
                EntityType.PLAYER,
                0,                          // data (unused for players)
                Vec3.ZERO,                  // velocity
                loc.getYaw()                // head yaw
        );
    }

    /** Removes the NPC entity from the client. */
    public static ClientboundRemoveEntitiesPacket removePacket(int entityId) {
        return new ClientboundRemoveEntitiesPacket(entityId);
    }

    // -------------------------------------------------------------------------
    // Movement & head rotation
    // -------------------------------------------------------------------------

    /**
     * Teleport packet for large moves or initial positioning.
     */
    public static ClientboundTeleportEntityPacket teleportPacket(NpcState state) {
        Location loc = state.currentLocation();
        return new ClientboundTeleportEntityPacket(
                state.entityId(),
                new net.minecraft.world.entity.PositionMoveRotation(
                        new Vec3(loc.getX(), loc.getY(), loc.getZ()),
                        Vec3.ZERO,
                        loc.getYaw(),
                        loc.getPitch()
                ),
                java.util.Set.of(),
                true   // on ground
        );
    }

    /**
     * Rotate-head packet so the NPC's skull tracks a direction.
     *
     * @param entityId  NPC entity id
     * @param yawDeg    Head yaw in degrees
     */
    public static ClientboundRotateHeadPacket headRotationPacket(int entityId, float yawDeg) {
        // Pack degrees → byte  (1 byte = 360°/256 ≈ 1.41°)
        byte packed = (byte) Math.floor(yawDeg * 256.0f / 360.0f);
        return new ClientboundRotateHeadPacket(entityId, packed);
    }

    // -------------------------------------------------------------------------
    // Entity data (metadata)
    // -------------------------------------------------------------------------

    /**
     * Sends entity metadata that marks the NPC as sneaking / not sneaking, etc.
     * For 1.21 we keep it minimal – just ensure the entity is rendered normally.
     */
    public static ClientboundSetEntityDataPacket metaPacket(int entityId) {
        // byte 0: shared entity flags (0 = normal, no fire, no sneaking, etc.)
        List<SynchedEntityData.DataValue<?>> values = List.of(
                SynchedEntityData.DataValue.create(
                        net.minecraft.world.entity.Entity.DATA_SHARED_FLAGS_ID,
                        (byte) 0
                )
        );
        return new ClientboundSetEntityDataPacket(entityId, values);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a minimal {@link ServerPlayer} object whose profile is used by
     * {@link ClientboundPlayerInfoUpdatePacket#createPlayerInitializing}.
     *
     * <p>We delegate to a static factory provided by CraftBukkit internals,
     * avoiding the need to fully initialise the player's connection.</p>
     */
    private static ServerPlayer buildDummyServerPlayer(GameProfile profile) {
        MinecraftServer server = MinecraftServer.getServer();
        ServerLevel     level  = server.overworld();
        return new ServerPlayer(server, level, profile,
                net.minecraft.server.level.ClientInformation.createDefault());
    }

    // -------------------------------------------------------------------------
    // Dispatch helpers
    // -------------------------------------------------------------------------

    /** Sends a packet to a single Bukkit player. */
    public static void send(Player player, Packet<?> packet) {
        ((CraftPlayer) player).getHandle().connection.send(packet);
    }

    /** Sends a packet to all online players. */
    public static void broadcast(Packet<?> packet) {
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            send(p, packet);
        }
    }
}
