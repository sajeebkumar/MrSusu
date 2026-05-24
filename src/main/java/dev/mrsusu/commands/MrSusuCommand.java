package dev.mrsusu.commands;

import dev.mrsusu.MrSusuPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Handles the {@code /mrsusu} command.
 *
 * <pre>
 * /mrsusu spawn   – spawns the NPC at the player's location and saves it
 * /mrsusu despawn – removes the NPC from the world
 * /mrsusu reload  – reloads config.yml (does not respawn NPC)
 * </pre>
 */
public final class MrSusuCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.GOLD + "[MrSusu] " + ChatColor.RESET;

    private final MrSusuPlugin plugin;

    public MrSusuCommand(MrSusuPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!sender.hasPermission("mrsusu.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn"   -> handleSpawn(sender);
            case "despawn" -> handleDespawn(sender);
            case "reload"  -> handleReload(sender);
            default        -> { sendHelp(sender); yield true; }
        };
    }

    // -------------------------------------------------------------------------
    // Sub-commands
    // -------------------------------------------------------------------------

    private boolean handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "This command can only be run by a player.");
            return true;
        }

        if (plugin.getNpcManager().isSpawned()) {
            plugin.getNpcManager().despawn();
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Previous NPC despawned. Spawning a new one…");
        }

        plugin.getDataManager().saveLocation(player.getLocation());
        plugin.getNpcManager().spawn(player.getLocation());
        player.sendMessage(PREFIX + ChatColor.GREEN + "Mr Susu has been spawned at your location!");
        player.sendMessage(PREFIX + ChatColor.GRAY + "Location saved for auto-spawn on server restart.");
        return true;
    }

    private boolean handleDespawn(CommandSender sender) {
        if (!plugin.getNpcManager().isSpawned()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "The NPC is not currently spawned.");
            return true;
        }
        plugin.getNpcManager().despawn();
        plugin.getDataManager().clearLocation();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Mr Susu has been despawned and spawn data cleared.");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getSkinFetcher().invalidateCache();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Config reloaded! Skin cache cleared.");
        sender.sendMessage(PREFIX + ChatColor.GRAY + "Note: Respawn the NPC to apply new skin settings.");
        return true;
    }

    // -------------------------------------------------------------------------

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "━━━━━━ MrSusu NPC ━━━━━━");
        sender.sendMessage(ChatColor.YELLOW + "/mrsusu spawn"   + ChatColor.GRAY + " – Spawn NPC at your location");
        sender.sendMessage(ChatColor.YELLOW + "/mrsusu despawn" + ChatColor.GRAY + " – Remove the NPC");
        sender.sendMessage(ChatColor.YELLOW + "/mrsusu reload"  + ChatColor.GRAY + " – Reload config.yml");
    }

    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return List.of("spawn", "despawn", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
