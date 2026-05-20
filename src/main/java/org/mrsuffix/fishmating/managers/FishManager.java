package com.mrsuffix.fishmating.managers;

import com.mrsuffix.fishmating.FishMatingPlugin;
import com.mrsuffix.fishmating.models.FishData;
import com.mrsuffix.fishmating.utils.ParticleUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages fish behavior, movement, and seed interaction
 */
public class FishManager {

    private final FishMatingPlugin plugin;
    private final Map<UUID, FishData> fishDataMap;
    private BukkitTask fishUpdateTask;

    public FishManager(FishMatingPlugin plugin) {
        this.plugin = plugin;
        this.fishDataMap = new ConcurrentHashMap<>();
        startFishUpdateTask();
    }

    /**
     * Starts the periodic task that updates fish behavior
     */
    private void startFishUpdateTask() {
        fishUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                updateAllFish();
            } catch (Exception e) {
                plugin.getLogger().warning("Error in fish update task: " + e.getMessage());
            }
        }, 20L, 10L); // Run every 0.5 seconds
    }

    /**
     * Updates all tracked fish
     */
    private void updateAllFish() {
        ConfigManager config = plugin.getConfigManager();

        // Clean up invalid fish data
        fishDataMap.entrySet().removeIf(entry -> {
            Entity entity = entry.getValue().getEntity();
            return !entity.isValid() || entity.isDead();
        });

        // Update each fish
        for (FishData fishData : fishDataMap.values()) {
            try {
                updateFish(fishData, config);
            } catch (Exception e) {
                plugin.getLogger().warning("Error updating fish: " + e.getMessage());
            }
        }
    }

    /**
     * Updates a single fish's behavior
     * @param fishData The fish data to update
     * @param config The configuration manager
     */
    private void updateFish(FishData fishData, ConfigManager config) {
        Entity fish = fishData.getEntity();

        // Check for breeding timeout
        if (fishData.hasBreedingTimedOut(config.getBreedingTimeoutSeconds())) {
            fishData.setBreedingReady(false);
            fishData.setTargetSeed(null);
        }

        // Show particles if breeding ready
        if (fishData.isBreedingReady() && config.isParticlesEnabled()) {
            ParticleUtils.showHeartParticles(fish.getLocation(), config.getParticleCount());
        }

        // Find and move toward seeds if not breeding ready
        if (!fishData.isBreedingReady() && fishData.canBreed(config.getBreedingCooldownMinutes())) {
            handleSeedSeeking(fishData, config);
        }
    }

    /**
     * Handles fish seeking behavior for seeds
     * @param fishData The fish data
     * @param config The configuration manager
     */
    private void handleSeedSeeking(FishData fishData, ConfigManager config) {
        Entity fish = fishData.getEntity();
        Material requiredSeed = config.getSeedForFish(fish.getType());

        if (requiredSeed == null) return;

        // Check if current target is still valid
        Entity currentTarget = fishData.getTargetSeed();
        if (currentTarget != null && (!currentTarget.isValid() || currentTarget.isDead())) {
            fishData.setTargetSeed(null);
            currentTarget = null;
        }

        // Find nearest matching seed if no current target
        if (currentTarget == null) {
            Item nearestSeed = findNearestSeed(fish.getLocation(), requiredSeed, config.getDetectionRadius());
            if (nearestSeed != null) {
                fishData.setTargetSeed(nearestSeed);
                currentTarget = nearestSeed;
            }
        }

        // Move toward target seed
        if (currentTarget instanceof Item) {
            Item seedItem = (Item) currentTarget;
            moveTowardSeed(fish, seedItem);

            // Check if fish reached the seed
            if (fish.getLocation().distance(seedItem.getLocation()) <= 1.5) {
                consumeSeed(fishData, seedItem);
            }
        }
    }

    /**
     * Finds the nearest seed of the specified type within radius
     * @param location The center location
     * @param seedType The seed material type
     * @param radius The search radius
     * @return The nearest seed item, or null if none found
     */
    private Item findNearestSeed(Location location, Material seedType, double radius) {
        // Filter to items during the AABB scan, then compare squared distances to
        // avoid the per-candidate sqrt of Location#distance.
        Item nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;

        for (Entity entity : location.getWorld()
                .getNearbyEntities(location, radius, radius, radius, e -> e instanceof Item)) {
            Item item = (Item) entity;
            if (item.getItemStack().getType() != seedType) {
                continue;
            }
            if (!isInWater(item.getLocation())) {
                continue;
            }
            double distanceSq = location.distanceSquared(item.getLocation());
            if (distanceSq < nearestDistanceSq) {
                nearestDistanceSq = distanceSq;
                nearest = item;
            }
        }
        return nearest;
    }

    /**
     * Moves a fish toward a seed item
     * @param fish The fish entity
     * @param seed The seed item
     */
    private void moveTowardSeed(Entity fish, Item seed) {
        Location fishLoc = fish.getLocation();
        Location seedLoc = seed.getLocation();

        Vector direction = seedLoc.toVector().subtract(fishLoc.toVector());

        // Fish is essentially on the seed: normalizing a zero vector yields NaN, so
        // skip applying velocity (the consume check will pick it up this tick).
        if (direction.lengthSquared() < 1.0E-6) {
            return;
        }

        Vector velocity = direction.normalize().multiply(0.3); // Moderate speed

        // Ensure fish stays in water
        velocity.setY(Math.max(velocity.getY(), -0.1));

        fish.setVelocity(velocity);
    }

    /**
     * Handles fish consuming a seed
     * @param fishData The fish data
     * @param seedItem The seed item
     */
    private void consumeSeed(FishData fishData, Item seedItem) {
        try {
            // Reduce seed stack size. Item.getItemStack() returns a copy, so the
            // modified stack must be written back with setItemStack().
            ItemStack stack = seedItem.getItemStack();
            int currentAmount = stack.getAmount();
            if (currentAmount > 1) {
                stack.setAmount(currentAmount - 1);
                seedItem.setItemStack(stack);
            } else {
                seedItem.remove();
            }

            // Set fish as breeding ready
            fishData.setBreedingReady(true);
            fishData.setTargetSeed(null);

            // Show consumption particles
            if (plugin.getConfigManager().isParticlesEnabled()) {
                ParticleUtils.showConsumptionParticles(fishData.getEntity().getLocation());
            }

            plugin.getLogger().fine(() -> "Fish consumed seed and is now breeding ready");

        } catch (Exception e) {
            plugin.getLogger().warning("Error during seed consumption: " + e.getMessage());
        }
    }

    /**
     * Checks if a location is in water
     * @param location The location to check
     * @return True if the location is in water
     */
    private boolean isInWater(Location location) {
        Material blockType = location.getBlock().getType();
        return blockType == Material.WATER;
    }

    /**
     * Gets or creates fish data for an entity
     * @param fish The fish entity
     * @return The fish data
     */
    public FishData getFishData(Entity fish) {
        return fishDataMap.computeIfAbsent(fish.getUniqueId(), uuid -> new FishData(fish));
    }

    /**
     * Removes fish data for an entity
     * @param fish The fish entity
     */
    public void removeFishData(Entity fish) {
        fishDataMap.remove(fish.getUniqueId());
    }

    /**
     * Begins tracking an entity if its type has a configured seed mapping. Safe to
     * call repeatedly (tracking is keyed by UUID) and for non-fish entities (ignored).
     * This is the event-driven entry point used by spawn / chunk-load handlers.
     * @param entity The entity to consider tracking
     */
    public void trackFish(Entity entity) {
        if (plugin.getConfigManager().getSeedForFish(entity.getType()) == null) {
            return;
        }
        UUID id = entity.getUniqueId();
        // Bound automatic tracking to avoid unbounded growth on busy/abused servers.
        // Already-tracked fish are always allowed through (idempotent re-tracking).
        if (!fishDataMap.containsKey(id)
                && fishDataMap.size() >= plugin.getConfigManager().getMaxTrackedFish()) {
            return;
        }
        fishDataMap.computeIfAbsent(id, uuid -> new FishData(entity));
    }

    /**
     * One-time scan of already-loaded worlds for mapped fish, tracking each. Intended
     * to run on enable so fish that existed before the plugin started (e.g. after a
     * reload, or in already-loaded chunks) are picked up without per-tick polling.
     */
    public void trackExistingFish() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                trackFish(entity);
            }
        }
    }

    /**
     * Returns a snapshot of all currently tracked fish. Iterating the snapshot is
     * safe against concurrent map changes.
     * @return A copy of the tracked fish data
     */
    public Collection<FishData> getTrackedFish() {
        return new ArrayList<>(fishDataMap.values());
    }

    /**
     * Shuts down the fish manager
     */
    public void shutdown() {
        if (fishUpdateTask != null && !fishUpdateTask.isCancelled()) {
            fishUpdateTask.cancel();
        }
        fishDataMap.clear();
    }
}
