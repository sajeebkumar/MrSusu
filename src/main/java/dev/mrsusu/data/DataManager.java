package dev.mrsusu.data;

import dev.mrsusu.MrSusuPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Persists and loads the NPC spawn location to/from npc_data.yml.
 */
public final class DataManager {

    private final MrSusuPlugin plugin;
    private final File          dataFile;
    private YamlConfiguration   cfg;

    public DataManager(MrSusuPlugin plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "npc_data.yml");
        if (!dataFile.exists()) {
            plugin.saveResource("npc_data.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(dataFile);
    }

    // -------------------------------------------------------------------------

    /** Returns true if a valid world + coordinates have been saved. */
    public boolean hasSavedLocation() {
        String world = cfg.getString("spawn.world", "");
        return !world.isBlank() && Bukkit.getWorld(world) != null;
    }

    /** Loads and returns the saved Location, or null if none / world missing. */
    public Location loadLocation() {
        String worldName = cfg.getString("spawn.world", "");
        if (worldName.isBlank()) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x     = cfg.getDouble("spawn.x", 0);
        double y     = cfg.getDouble("spawn.y", 64);
        double z     = cfg.getDouble("spawn.z", 0);
        float  yaw   = (float) cfg.getDouble("spawn.yaw", 0);
        float  pitch = (float) cfg.getDouble("spawn.pitch", 0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    /** Saves the given location to npc_data.yml. */
    public void saveLocation(Location loc) {
        cfg.set("spawn.world", loc.getWorld().getName());
        cfg.set("spawn.x",     loc.getX());
        cfg.set("spawn.y",     loc.getY());
        cfg.set("spawn.z",     loc.getZ());
        cfg.set("spawn.yaw",   (double) loc.getYaw());
        cfg.set("spawn.pitch", (double) loc.getPitch());
        flush();
    }

    /** Clears the saved location from file. */
    public void clearLocation() {
        cfg.set("spawn.world", "");
        flush();
    }

    // -------------------------------------------------------------------------

    private void flush() {
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[MrSusu] Failed to save npc_data.yml: " + e.getMessage());
        }
    }
}
