package dev.mrsusu;

import dev.mrsusu.commands.MrSusuCommand;
import dev.mrsusu.data.DataManager;
import dev.mrsusu.listeners.PacketListener;
import dev.mrsusu.listeners.PlayerJoinListener;
import dev.mrsusu.npc.NpcManager;
import dev.mrsusu.skin.SkinFetcher;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * MrSusu – standalone fake-player NPC plugin for Paper 1.21.1 – 1.21.4+
 * Uses Mojang-mapped NMS (net.minecraft.server) via Paper's reflections-free API.
 */
public final class MrSusuPlugin extends JavaPlugin {

    private static MrSusuPlugin instance;

    private DataManager   dataManager;
    private SkinFetcher   skinFetcher;
    private NpcManager    npcManager;
    private PacketListener packetListener;

    @Override
    public void onEnable() {
        instance = this;

        // --- Config -----------------------------------------------------------
        saveDefaultConfig();

        // --- Sub-systems ------------------------------------------------------
        dataManager    = new DataManager(this);
        skinFetcher    = new SkinFetcher(this);
        npcManager     = new NpcManager(this);
        packetListener = new PacketListener(this);

        // --- Commands ---------------------------------------------------------
        MrSusuCommand cmd = new MrSusuCommand(this);
        getCommand("mrsusu").setExecutor(cmd);
        getCommand("mrsusu").setTabCompleter(cmd);

        // --- Listeners --------------------------------------------------------
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        // --- Auto-spawn on startup --------------------------------------------
        Bukkit.getScheduler().runTaskLater(this, this::attemptAutoSpawn, 40L);

        getLogger().info("MrSusu NPC plugin enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (npcManager != null) {
            npcManager.despawn();
        }
        getLogger().info("MrSusu NPC plugin disabled.");
    }

    // -------------------------------------------------------------------------

    /** Attempts to spawn NPC from saved data file on startup. */
    private void attemptAutoSpawn() {
        if (dataManager.hasSavedLocation()) {
            getLogger().info("[MrSusu] Auto-spawning NPC from saved location…");
            npcManager.spawnFromSaved();
        } else {
            getLogger().info("[MrSusu] No saved spawn location found – use /mrsusu spawn.");
        }
    }

    // -------------------------------------------------------------------------
    // Static accessors
    // -------------------------------------------------------------------------

    public static MrSusuPlugin getInstance() { return instance; }

    public DataManager    getDataManager()    { return dataManager; }
    public SkinFetcher    getSkinFetcher()     { return skinFetcher; }
    public NpcManager     getNpcManager()      { return npcManager; }
    public PacketListener getPacketListener()  { return packetListener; }

    public void debug(String msg) {
        if (getConfig().getBoolean("debug", false)) {
            getLogger().log(Level.INFO, "[DEBUG] " + msg);
        }
    }
}
