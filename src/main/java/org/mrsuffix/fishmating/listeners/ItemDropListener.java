package com.mrsuffix.fishmating.listeners;

import com.mrsuffix.fishmating.FishMatingPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;

/**
 * Drives event-driven seed seeking: when a breeding seed lands in water, nearby eligible
 * fish are attracted to it. This replaces a per-fish, per-tick world scan with a single
 * bounded scan per seed drop (see {@link com.mrsuffix.fishmating.managers.FishManager#attractFishToSeed}).
 */
public class ItemDropListener implements Listener {

    private final FishMatingPlugin plugin;

    public ItemDropListener(FishMatingPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * On a breeding seed spawning in water, schedule fish attraction for the next tick.
     *
     * <p>The one-tick defer (a) lets a player drop's {@code Item#getThrower} populate before
     * {@code attractFishToSeed} reads it for the {@code require-player-thrown-seeds} gate, and
     * (b) lets the item settle (it may merge, be picked up, or move out of water), which the
     * attraction re-validates. The cheap material/water filter happens first so the very
     * frequent non-seed spawn path schedules nothing.
     *
     * @param event The item spawn event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        try {
            Item item = event.getEntity();
            Material itemType = item.getItemStack().getType();

            if (plugin.getConfigManager().isBreedingSeed(itemType) && isInWater(item)) {
                plugin.getLogger().fine(() -> "Breeding seed dropped in water: " + itemType);
                Bukkit.getScheduler().runTask(plugin,
                        () -> plugin.getFishManager().attractFishToSeed(item));
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error handling item spawn: " + e.getMessage());
        }
    }

    /**
     * Checks if an item is in water
     * @param item The item to check
     * @return True if the item is in water
     */
    private boolean isInWater(Item item) {
        Material blockType = item.getLocation().getBlock().getType();
        return blockType == Material.WATER;
    }
}
