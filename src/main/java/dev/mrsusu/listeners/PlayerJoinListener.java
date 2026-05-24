package dev.mrsusu.listeners;

import dev.mrsusu.MrSusuPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Sends NPC spawn packets to newly joining players when the NPC is active.
 */
public final class PlayerJoinListener implements Listener {

    private final MrSusuPlugin plugin;

    public PlayerJoinListener(MrSusuPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getNpcManager().isSpawned()) return;

        // Delay by 10 ticks so the client has fully loaded before receiving NPC packets
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.getNpcManager().sendSpawnPacketsToPlayer(event.getPlayer()),
                10L);
    }
}
