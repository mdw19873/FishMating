package com.mrsuffix.fishmating.managers;

import com.mrsuffix.fishmating.FishMatingPlugin;
import com.mrsuffix.fishmating.models.FishData;
import com.mrsuffix.fishmating.utils.ParticleUtils;
import com.mrsuffix.fishmating.utils.PlayerProximityUtil;
import com.mrsuffix.fishmating.utils.ScaleUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
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

    /** Tick period of the fish update task (every 0.5s). */
    private static final long UPDATE_PERIOD_TICKS = 10L;

    /**
     * How often a growing fish's scale is actually advanced (~1.5s). Coarser than the
     * update period so growth emits fewer attribute-update packets, while the maturity
     * check still reads the scale every tick. Must be a multiple of UPDATE_PERIOD_TICKS.
     */
    private static final long GROWTH_PERIOD_TICKS = 30L;
    private static final long UPDATES_PER_GROWTH_STEP = GROWTH_PERIOD_TICKS / UPDATE_PERIOD_TICKS;

    /** Vanilla TemptGoal stop distance: a tempted fish mills this close to the player (blocks). */
    private static final double FOLLOW_STOP_DISTANCE = 2.5;
    /** Approach speed toward a tempting player; matches the seed-approach speed. */
    private static final double FOLLOW_SPEED = 0.3;

    private final FishMatingPlugin plugin;
    private final Map<UUID, FishData> fishDataMap;
    private BukkitTask fishUpdateTask;
    private long updateCounter;

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
        }, 20L, UPDATE_PERIOD_TICKS); // Run every 0.5 seconds
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

        // Advance growth only on every Nth cycle to limit attribute-update packets.
        boolean growthTick = (updateCounter++ % UPDATES_PER_GROWTH_STEP) == 0;

        // Held-seed temptation: assign follow targets by scanning players (not fish) before the
        // per-fish loop, so a fish tempted this cycle also moves this cycle.
        if (config.isSeedTemptation()) {
            assignFollowTargets(config);
        }

        // Update each fish
        for (FishData fishData : fishDataMap.values()) {
            try {
                updateFish(fishData, config, growthTick);
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
    private void updateFish(FishData fishData, ConfigManager config, boolean growthTick) {
        Entity fish = fishData.getEntity();

        // Natural growth: advance the scale toward 1.0 on growth ticks (not wall clock), so
        // growth pauses while the fish is unloaded and resumes from the persisted scale after
        // a restart. Reads/writes the scale attribute only when natural-growth is enabled.
        if (config.isNaturalGrowth() && growthTick) {
            double scale = ScaleUtil.getScale(fish);
            if (scale < 1.0) {
                ScaleUtil.setScale(fish, grownScale(scale, config.getBabyScale(),
                        config.getGrowthDurationMinutes(), GROWTH_PERIOD_TICKS));
            }
        }

        // Check for breeding timeout
        if (fishData.hasBreedingTimedOut(config.getBreedingTimeoutSeconds())) {
            fishData.setBreedingReady(false);
            fishData.setTargetSeed(null);
        }

        // No per-tick heart particles here. Vanilla Java shows love-mode hearts only once
        // (when the animal is fed), not continuously, due to MC-93826 — so the single burst
        // lives in consumeSeed(). See the note there before changing this.

        // Seed discovery is push-based: ItemDropListener assigns a target seed when one lands
        // in water nearby (see attractFishToSeed), so there is NO per-tick world scan for the
        // common case. We only advance toward an already-assigned target.
        //
        // Safety net — a single bounded rescan fires on TRANSITIONS (never every idle tick):
        //   * the fish just became eligible (matured / off cooldown / readiness expired /
        //     required player arrived), detected via the wasEligibleToSeek rising edge; or
        //   * its target was just lost (eaten by another fish / despawned) while it stays
        //     eligible — so it can re-acquire a still-present nearby seed instead of giving up.
        boolean eligible = isEligibleToSeek(fishData, fish, config);
        boolean roseToEligible = eligible && !fishData.wasEligibleToSeek();
        fishData.setWasEligibleToSeek(eligible);

        boolean hadTarget = fishData.getTargetSeed() != null;
        if (hadTarget) {
            if (eligible) {
                advanceTowardTarget(fishData, config);
            } else {
                fishData.setTargetSeed(null);
            }
        }
        // Lost (not consumed): had a target, it's now gone, and we did not become breeding-ready.
        boolean lostTarget = hadTarget && fishData.getTargetSeed() == null && !fishData.isBreedingReady();

        if (eligible && fishData.getTargetSeed() == null && !fishData.isBreedingReady()
                && (roseToEligible || lostTarget)) {
            rescanForSeed(fishData, fish, config);
        }

        // Held-seed temptation (herding): follow a tempting player, but only when not committed
        // to a thrown seed — the seed always wins. Targets are assigned by assignFollowTargets;
        // here we advance/invalidate them with cheap field reads + one held-item read.
        if (config.isSeedTemptation() && fishData.getTargetSeed() == null) {
            advanceTowardFollowTarget(fishData, fish, config);
        } else if (fishData.getFollowTarget() != null) {
            fishData.setFollowTarget(null); // a seed took over, or temptation was disabled
        }
    }

    /**
     * One growth step toward full size, spreading baby→adult across {@code durationMinutes}
     * expressed in update-task periods. Pure (no entity/attribute access) so the growth
     * pacing is unit-testable independent of the live scale attribute.
     *
     * @return the next scale, never exceeding 1.0
     */
    static double grownScale(double current, double babyScale, int durationMinutes, long periodTicks) {
        double updatesToMature = (durationMinutes * 60.0 * 20.0) / periodTicks;
        double delta = (1.0 - babyScale) / updatesToMature;
        return Math.min(1.0, current + delta);
    }

    /**
     * Event-driven seed attraction: when a breeding seed lands in water, assign it as the
     * target for nearby eligible fish of the matching type. This replaces the old per-fish,
     * per-tick world scan with a single bounded scan per seed drop. Called (deferred one
     * tick) from {@link com.mrsuffix.fishmating.listeners.ItemDropListener}.
     *
     * <p>Main thread only (Bukkit entity access). Only tracked, type-matching, eligible fish
     * with no current target are attracted, so a fish commits to its first seed rather than
     * thrashing between several that land together.
     *
     * @param seed the seed item that spawned in water
     */
    public void attractFishToSeed(Item seed) {
        if (seed == null || !seed.isValid() || seed.isDead()) {
            return;
        }
        ConfigManager config = plugin.getConfigManager();
        Material seedType = seed.getItemStack().getType();

        // Respect the player-thrown gate (blocks dispenser/dropper farms). The one-tick defer
        // in the listener ensures a player drop's thrower is populated before we read it.
        if (config.isRequirePlayerThrownSeeds() && seed.getThrower() == null) {
            return;
        }
        if (!isInWater(seed.getLocation())) {
            return;
        }

        double radius = config.getDetectionRadius();
        // Filter the AABB scan to fish whose configured seed matches this drop, then attract
        // only tracked, eligible, target-less ones.
        for (Entity entity : seed.getWorld().getNearbyEntities(seed.getLocation(), radius, radius, radius,
                e -> config.getSeedForFish(e.getType()) == seedType)) {
            FishData fishData = fishDataMap.get(entity.getUniqueId());
            if (fishData == null || fishData.getTargetSeed() != null) {
                continue;
            }
            if (isEligibleToSeek(fishData, entity, config)) {
                fishData.setTargetSeed(seed);
                plugin.getLogger().fine(() -> "Fish attracted to seed: " + entity.getType());
            }
        }
    }

    /**
     * Whether a fish may currently seek/consume a seed: full-grown, not already
     * breeding-ready, off cooldown, and within range of a player (when
     * {@code require-player-within} is set; 0 disables that gate).
     */
    private boolean isEligibleToSeek(FishData fishData, Entity fish, ConfigManager config) {
        return ScaleUtil.isFullGrown(fish)
                && !fishData.isBreedingReady()
                && fishData.canBreed(config.getBreedingCooldownMinutes())
                && PlayerProximityUtil.playerWithin(fish, config.getRequirePlayerWithin());
    }

    /**
     * Held-seed temptation scan pass: for each non-spectator player holding a matching breeding
     * seed, one bounded {@code getNearbyEntities} scan around that player assigns the player as
     * the follow target of nearby tracked, matching, full-grown, seed-less fish. Herding only —
     * it never changes breeding state.
     *
     * <p>Main thread only. The number of scans is bounded by players-holding-a-seed, NOT by the
     * tracked-fish count, so this preserves the no-per-fish-scan rule (see CLAUDE.md Performance).
     * A fish with a thrown-seed target is skipped: the seed always wins.
     *
     * @param config the configuration manager
     */
    private void assignFollowTargets(ConfigManager config) {
        double radius = config.getTemptationRadius();
        if (radius <= 0) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Material held = config.heldBreedingSeed(
                    player.getInventory().getItemInMainHand(),
                    player.getInventory().getItemInOffHand());
            if (held == null) {
                continue;
            }
            for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(),
                    radius, radius, radius, e -> config.getSeedForFish(e.getType()) == held)) {
                FishData fishData = fishDataMap.get(entity.getUniqueId());
                if (fishData == null || fishData.getTargetSeed() != null) {
                    continue; // untracked, or already committed to a thrown seed
                }
                if (ScaleUtil.isFullGrown(entity)) {
                    fishData.setFollowTarget(player);
                }
            }
        }
    }

    /**
     * Advances a fish toward the player it is being tempted by, keeping it in water and stopping
     * within {@link #FOLLOW_STOP_DISTANCE}. Clears the follow target ("loses interest") when the
     * player is gone, spectating, in another world, beyond {@code temptation-radius}, or no longer
     * holding the seed that matches this fish. Cheap: field reads plus one held-item read.
     *
     * @param fishData the fish data (with a follow target to evaluate)
     * @param fish the fish entity
     * @param config the configuration manager
     */
    private void advanceTowardFollowTarget(FishData fishData, Entity fish, ConfigManager config) {
        if (!(fishData.getFollowTarget() instanceof Player player)
                || !player.isOnline()
                || player.getGameMode() == GameMode.SPECTATOR
                || !player.getWorld().equals(fish.getWorld())) {
            fishData.setFollowTarget(null);
            return;
        }
        double radius = config.getTemptationRadius();
        double dx = player.getX() - fish.getX();
        double dy = player.getY() - fish.getY();
        double dz = player.getZ() - fish.getZ();
        if (dx * dx + dy * dy + dz * dz > radius * radius) {
            fishData.setFollowTarget(null); // wandered out of range
            return;
        }
        Material held = config.heldBreedingSeed(
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand());
        if (held == null || config.getSeedForFish(fish.getType()) != held) {
            fishData.setFollowTarget(null); // stopped holding a matching seed
            return;
        }
        Vector velocity = followVelocity(fish.getX(), fish.getY(), fish.getZ(),
                player.getX(), player.getY(), player.getZ(), FOLLOW_STOP_DISTANCE, FOLLOW_SPEED);
        if (velocity != null) {
            fish.setVelocity(velocity);
        }
    }

    /**
     * Horizontal-biased approach velocity toward a tempting player, kept in water. Pure (no
     * entity/Bukkit access) so it is unit-testable without physics. Returns {@code null} when the
     * fish is within {@code stopDistance} horizontally (mill, no push) or essentially on the
     * player. Positive Y is clamped to 0 then floored at -0.1, so a player standing above/beside
     * the water never lifts the fish out of it.
     *
     * @return the velocity to apply, or {@code null} to apply none this tick
     */
    static Vector followVelocity(double fx, double fy, double fz,
                                 double px, double py, double pz,
                                 double stopDistance, double speed) {
        double dx = px - fx;
        double dz = pz - fz;
        if (dx * dx + dz * dz <= stopDistance * stopDistance) {
            return null; // close enough horizontally: mill near the player
        }
        double dy = py - fy;
        Vector direction = new Vector(dx, dy, dz);
        if (direction.lengthSquared() < 1.0E-6) {
            return null;
        }
        Vector velocity = direction.normalize().multiply(speed);
        // Never push the fish upward toward a player above the water; a gentle sink is fine.
        velocity.setY(Math.max(Math.min(velocity.getY(), 0.0), -0.1));
        return velocity;
    }

    /**
     * Advances a fish toward its already-assigned target seed and consumes it on arrival.
     * The target is assigned event-driven by {@link #attractFishToSeed}; this does no seed
     * discovery. A dead/invalid target is cleared.
     *
     * @param fishData the fish data (with a non-null target seed)
     * @param config the configuration manager
     */
    private void advanceTowardTarget(FishData fishData, ConfigManager config) {
        Entity fish = fishData.getEntity();
        if (!(fishData.getTargetSeed() instanceof Item seedItem) || !seedItem.isValid() || seedItem.isDead()) {
            fishData.setTargetSeed(null);
            return;
        }

        moveTowardSeed(fish, seedItem);

        // Consume once within range. MockBukkit has no physics, so a test fish must be
        // spawned within this range to "reach" the seed (see TESTING.md).
        if (fish.getLocation().distance(seedItem.getLocation()) <= 1.5) {
            consumeSeed(fishData, seedItem);
        }
    }

    /**
     * One-shot transition rescan: find the nearest matching in-water seed around the fish and
     * target it. Called only on an eligibility rising edge or target loss (see {@link
     * #updateFish}), never on every idle tick, so it restores opportunistic re-targeting
     * (a fish that lost its seed, just matured, came off cooldown, etc. picks up a still-present
     * nearby seed) without reintroducing the old per-fish, per-tick polling.
     *
     * @param fishData the fish data (eligible and target-less)
     * @param fish the fish entity
     * @param config the configuration manager
     */
    private void rescanForSeed(FishData fishData, Entity fish, ConfigManager config) {
        Material requiredSeed = config.getSeedForFish(fish.getType());
        if (requiredSeed == null) {
            return;
        }
        Item nearest = findNearestSeed(fish.getLocation(), requiredSeed,
                config.getDetectionRadius(), config.isRequirePlayerThrownSeeds());
        if (nearest != null) {
            fishData.setTargetSeed(nearest);
            plugin.getLogger().fine(() -> "Fish re-acquired a seed on transition: " + fish.getType());
        }
    }

    /**
     * Finds the nearest matching seed of the given type within radius, in water, respecting the
     * player-thrown requirement. Used only by the transition rescan (not per tick).
     *
     * @param location the center location
     * @param seedType the seed material to match
     * @param radius the search radius
     * @param requirePlayerThrown when true, seeds with no thrower (dispenser/dropper) are skipped
     * @return the nearest qualifying seed item, or {@code null} if none
     */
    private Item findNearestSeed(Location location, Material seedType, double radius, boolean requirePlayerThrown) {
        // Filter to items during the AABB scan, then compare squared distances to avoid the
        // per-candidate sqrt of Location#distance.
        Item nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;

        for (Entity entity : location.getWorld()
                .getNearbyEntities(location, radius, radius, radius, e -> e instanceof Item)) {
            Item item = (Item) entity;
            if (item.getItemStack().getType() != seedType) {
                continue;
            }
            // A dispenser/dropper-spawned seed has no thrower; require one to block fully
            // automated breeding farms when configured.
            if (requirePlayerThrown && item.getThrower() == null) {
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

            // Show the consumption puff plus a single heart burst on entering breeding
            // readiness, matching vanilla: feeding an animal puts it in love mode and shows
            // hearts. NOTE: in current Java Edition the love-mode hearts appear only ONCE (a
            // long-standing bug, MC-93826, https://bugs.mojang.com/browse/MC-93826) rather
            // than continuously as intended. We match that observed behavior for consistency.
            // If MC-93826 is ever fixed so vanilla shows hearts continuously, restore a
            // per-tick emission in updateFish().
            if (plugin.getConfigManager().isParticlesEnabled()) {
                Location loc = fishData.getEntity().getLocation();
                ParticleUtils.showConsumptionParticles(loc);
                ParticleUtils.showHeartParticles(loc, plugin.getConfigManager().getParticleCount());
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
