package com.mrsuffix.fishmating.listeners;

import com.mrsuffix.fishmating.FishMatingPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Drives event-driven seed seeking: when a breeding seed comes to rest in water, nearby
 * eligible fish are attracted to it. This replaces a per-fish, per-tick world scan with a
 * single bounded scan per seed drop (see
 * {@link com.mrsuffix.fishmating.managers.FishManager#attractFishToSeed}).
 */
public class ItemDropListener implements Listener {

    /** How often (ticks) a freshly spawned seed is re-checked for landing in water. */
    private static final long WATCH_PERIOD_TICKS = 5L;

    /**
     * Max number of in-water checks before giving up on a seed (≈ {@value #MAX_WATCH_CHECKS} ×
     * {@link #WATCH_PERIOD_TICKS} ticks). A thrown seed reaches the water within ~1–2s; a seed
     * that never does (landed on dry land) is dropped after this window.
     */
    private static final int MAX_WATCH_CHECKS = 20;

    private final FishMatingPlugin plugin;

    public ItemDropListener(FishMatingPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * When a breeding seed spawns, watch it until it settles in water, then attract fish once.
     *
     * <p>A thrown seed's {@link ItemSpawnEvent} fires while the item is still in the player's
     * hand position — <em>in the air</em>, not yet in the water it's being tossed into — so we
     * cannot decide in-water at spawn. Instead we poll the seed for a few seconds until it comes
     * to rest in water (also giving a player drop's {@code Item#getThrower} time to populate for
     * the {@code require-player-thrown-seeds} gate), then run a single attraction scan. The
     * cheap material filter happens first so the very frequent non-seed spawn path starts no
     * watcher.
     *
     * @param event The item spawn event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        try {
            Item item = event.getEntity();
            if (!plugin.getConfigManager().isBreedingSeed(item.getItemStack().getType())) {
                return;
            }
            watchUntilInWater(item);
        } catch (Exception e) {
            plugin.getLogger().warning("Error handling item spawn: " + e.getMessage());
        }
    }

    /**
     * Polls a seed item until it is in water (then attracts fish and stops) or it becomes
     * invalid / the watch window elapses. Bounded and short-lived: at most
     * {@link #MAX_WATCH_CHECKS} cheap block checks per seed drop.
     */
    private void watchUntilInWater(Item seed) {
        new BukkitRunnable() {
            private int checks = 0;

            @Override
            public void run() {
                if (seed == null || !seed.isValid() || seed.isDead() || ++checks > MAX_WATCH_CHECKS) {
                    cancel();
                    return;
                }
                if (isInWater(seed)) {
                    plugin.getLogger().fine(() -> "Breeding seed settled in water; attracting fish");
                    plugin.getFishManager().attractFishToSeed(seed);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, WATCH_PERIOD_TICKS);
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
