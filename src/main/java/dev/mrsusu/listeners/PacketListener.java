package dev.mrsusu.listeners;

import dev.mrsusu.MrSusuPlugin;
import io.netty.channel.*;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Injects a Netty {@link ChannelDuplexHandler} into each player's pipeline
 * to intercept {@link ServerboundInteractPacket}.
 *
 * <p>When the intercepted entity ID matches the active NPC, the interaction
 * is cancelled and the configured {@code click-command} is executed.</p>
 *
 * <p>Includes a per-player click cooldown to prevent spam.</p>
 */
public final class PacketListener implements Listener {

    private static final String HANDLER_NAME = "mrsusu_packet_interceptor";

    private final MrSusuPlugin                    plugin;
    /** Last-click timestamp per player UUID. */
    private final Map<UUID, Long>                 cooldowns = new ConcurrentHashMap<>();

    public PacketListener(MrSusuPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Inject into already-online players (e.g. after /reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            inject(p);
        }
    }

    // -------------------------------------------------------------------------
    // Bukkit event hooks
    // -------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        inject(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        eject(event.getPlayer());
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Netty injection
    // -------------------------------------------------------------------------

    private void inject(Player player) {
        Channel channel = getChannel(player);
        if (channel.pipeline().get(HANDLER_NAME) != null) return; // already injected

        channel.pipeline().addBefore("packet_handler", HANDLER_NAME,
                new ChannelDuplexHandler() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof ServerboundInteractPacket interactPacket) {
                            if (handleInteract(player, interactPacket)) return; // cancelled
                        }
                        super.channelRead(ctx, msg);
                    }
                });
        plugin.debug("Injected packet interceptor for " + player.getName());
    }

    private void eject(Player player) {
        Channel channel = getChannel(player);
        if (channel.pipeline().get(HANDLER_NAME) != null) {
            channel.pipeline().remove(HANDLER_NAME);
        }
    }

    // -------------------------------------------------------------------------
    // Interaction handling
    // -------------------------------------------------------------------------

    /**
     * Handles an inbound interact packet.
     *
     * @return true if the packet should be cancelled (interaction consumed)
     */
    private boolean handleInteract(Player player, ServerboundInteractPacket packet) {
        int npcId = plugin.getNpcManager().getEntityId();
        if (npcId == -1) return false;

        // Check entity ID via reflection accessor
        int targetId = getEntityId(packet);
        if (targetId != npcId) return false;

        // Cooldown check
        int cooldownSecs = plugin.getConfig().getInt("click-cooldown-seconds", 3);
        if (cooldownSecs > 0) {
            long now  = System.currentTimeMillis();
            long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
            if (now - last < cooldownSecs * 1000L) {
                plugin.debug("Cooldown active for " + player.getName() + " – ignoring click.");
                return true; // cancel but don't execute
            }
            cooldowns.put(player.getUniqueId(), now);
        }

        // Run on main thread
        Bukkit.getScheduler().runTask(plugin, () -> onNpcClick(player));
        return true; // cancel original packet
    }

    private void onNpcClick(Player player) {
        // Send NPC messages if configured
        if (plugin.getConfig().getBoolean("show-messages-on-interact", true)) {
            var messages = plugin.getConfig().getStringList("npc-messages");
            for (String msg : messages) {
                if (msg != null && !msg.isBlank()) {
                    player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
                }
            }
        }

        // Execute click-command
        String command = plugin.getConfig().getString("click-command", "mrsusu").strip();
        if (!command.isBlank()) {
            plugin.debug("Executing click-command for " + player.getName() + ": /" + command);
            Bukkit.dispatchCommand(player, command);
        }
    }

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    private static Channel getChannel(Player player) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        return nmsPlayer.connection.connection.channel;
    }

    /**
     * Reads the entity ID from {@link ServerboundInteractPacket}.
     * In Mojang-mapped 1.21 the field is {@code entityId} (int).
     */
    private static int getEntityId(ServerboundInteractPacket packet) {
        try {
            var field = ServerboundInteractPacket.class.getDeclaredField("entityId");
            field.setAccessible(true);
            return (int) field.get(packet);
        } catch (ReflectiveOperationException e) {
            // Fallback: try obfuscated field index via Paper's accessor if mapping changes
            return -2;
        }
    }
}
