package com.mrsuffix.fishmating;

import com.mrsuffix.fishmating.commands.FishMatingCommand;
import com.mrsuffix.fishmating.listeners.EntityListener;
import com.mrsuffix.fishmating.listeners.ItemDropListener;
import com.mrsuffix.fishmating.integrations.WorldGuardHook;
import com.mrsuffix.fishmating.managers.BreedingManager;
import com.mrsuffix.fishmating.managers.ConfigManager;
import com.mrsuffix.fishmating.managers.FishManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * FishMating Plugin - Simulates fish breeding triggered by throwing seeds into water
 *
 * @author mrsuffix
 * @version 1.1.0
 * @since 1.21
 */
public class FishMatingPlugin extends JavaPlugin {

    private static FishMatingPlugin instance;
    private ConfigManager configManager;
    private FishManager fishManager;
    private BreedingManager breedingManager;

    @Override
    public void onLoad() {
        // Register the WorldGuard "allow-fish-breeding" flag here: WorldGuard's flag
        // registry locks once it enables, so onEnable() would be too late. Guarded by the
        // plugin lookup so WorldGuardHook (and its com.sk89q.* references) is only linked
        // when WorldGuard is actually present. catch Throwable also covers NoClassDefFoundError.
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                WorldGuardHook.registerFlag(getLogger());
                getLogger().info("Registered WorldGuard flag 'allow-fish-breeding'.");
            } catch (Throwable t) {
                getLogger().warning("WorldGuard present but flag registration failed: " + t.getMessage());
            }
        }
    }

    @Override
    public void onEnable() {
        instance = this;

        try {
            // Initialize managers
            this.configManager = new ConfigManager(this);
            this.fishManager = new FishManager(this);
            this.breedingManager = new BreedingManager(this);

            // Register event listeners
            getServer().getPluginManager().registerEvents(new ItemDropListener(this), this);
            getServer().getPluginManager().registerEvents(new EntityListener(this), this);

            // Register the admin command (/fishmating reload)
            PluginCommand fishMatingCommand = getCommand("fishmating");
            if (fishMatingCommand != null) {
                FishMatingCommand handler = new FishMatingCommand(this);
                fishMatingCommand.setExecutor(handler);
                fishMatingCommand.setTabCompleter(handler);
            } else {
                getLogger().warning("Command 'fishmating' is missing from plugin.yml");
            }

            // Pick up fish that already exist in loaded worlds (e.g. after a reload).
            // Ongoing discovery is event-driven via the listeners above.
            this.fishManager.trackExistingFish();

            getLogger().info("FishMating plugin has been enabled successfully!");
            getLogger().info("Version: " + getDescription().getVersion());
            getLogger().info("Author: " + getDescription().getAuthors().get(0));

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable FishMating plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            // Clean up managers
            if (breedingManager != null) {
                breedingManager.shutdown();
            }
            if (fishManager != null) {
                fishManager.shutdown();
            }

            getLogger().info("FishMating plugin has been disabled successfully!");

        } catch (Exception e) {
            getLogger().warning("Error during plugin disable: " + e.getMessage());
        }

        instance = null;
    }

    /**
     * Gets the plugin instance
     * @return The plugin instance
     */
    public static FishMatingPlugin getInstance() {
        return instance;
    }

    /**
     * Gets the configuration manager
     * @return The configuration manager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Gets the fish manager
     * @return The fish manager
     */
    public FishManager getFishManager() {
        return fishManager;
    }

    /**
     * Gets the breeding manager
     * @return The breeding manager
     */
    public BreedingManager getBreedingManager() {
        return breedingManager;
    }
}
