package com.mrsuffix.fishmating.listeners;

import com.mrsuffix.fishmating.FishMatingPlugin;
import com.mrsuffix.fishmating.utils.ScaleUtil;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

/**
 * Handles entity-related events for fish tracking.
 *
 * <p>Tracking is event-driven: fish are registered with the {@link
 * com.mrsuffix.fishmating.managers.FishManager} as they spawn or as their chunk's
 * entities load, and removed when they die. This avoids polling every entity in
 * every world each tick.
 */
public class EntityListener implements Listener {

    private final FishMatingPlugin plugin;

    public EntityListener(FishMatingPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Tracks newly spawned fish of a configured type.
     * @param event The entity spawn event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        try {
            Entity entity = event.getEntity();
            if (plugin.getConfigManager().getSeedForFish(entity.getType()) != null) {
                plugin.getFishManager().trackFish(entity);
                plugin.getLogger().fine(() -> "Tracking spawned fish: " + entity.getType());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error handling entity spawn: " + e.getMessage());
        }
    }

    /**
     * Tracks fish whose chunk entities have just loaded (e.g. an existing fish coming
     * back into a loaded chunk). {@code trackFish} ignores non-mapped entities.
     * @param event The entities load event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        try {
            for (Entity entity : event.getEntities()) {
                plugin.getFishManager().trackFish(entity);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error handling entities load: " + e.getMessage());
        }
    }

    /**
     * Vanilla parity / anti-abuse: a fish that has not yet grown to an adult drops no loot
     * and no experience, mirroring vanilla baby animals (which drop nothing until they
     * mature).
     *
     * <p>The four mapped fish (cod, salmon, pufferfish, tropical fish) are not {@code
     * Ageable}, so a "baby" is emulated purely by a shrunk {@code scale} attribute (see
     * {@link com.mrsuffix.fishmating.managers.BreedingManager}); to the server it is an
     * ordinary adult that would otherwise drop full loot + kill XP. Without this gate a
     * player could breed fish and immediately kill the newborns to harvest drops and XP,
     * bypassing the growth-time maturity gate entirely.
     *
     * <p>Runs at {@link EventPriority#HIGHEST} so the suppression is the authoritative final
     * loot state. When {@code natural-growth} is off no fish is ever shrunk, so every fish
     * reads full-grown here and drops normally.
     *
     * @param event The entity death event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void suppressImmatureFishDrops(EntityDeathEvent event) {
        try {
            Entity entity = event.getEntity();
            if (plugin.getConfigManager().getSeedForFish(entity.getType()) == null) {
                return;
            }
            if (!ScaleUtil.isFullGrown(entity)) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                plugin.getLogger().fine(() -> "Suppressed drops/XP for immature fish: " + entity.getType());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error suppressing immature fish drops: " + e.getMessage());
        }
    }

    /**
     * Handles entity death events to clean up fish data
     * @param event The entity death event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        try {
            Entity entity = event.getEntity();
            EntityType entityType = entity.getType();

            // Check if this was a trackable fish type
            if (plugin.getConfigManager().getSeedForFish(entityType) != null) {
                plugin.getFishManager().removeFishData(entity);
                plugin.getLogger().fine(() -> "Fish died, data cleaned up: " + entityType);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error handling entity death: " + e.getMessage());
        }
    }
}
