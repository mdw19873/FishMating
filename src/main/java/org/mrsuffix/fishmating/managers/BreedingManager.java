package com.mrsuffix.fishmating.managers;

import com.mrsuffix.fishmating.FishMatingPlugin;
import com.mrsuffix.fishmating.models.BreedingPair;
import com.mrsuffix.fishmating.models.FishData;
import com.mrsuffix.fishmating.utils.ParticleUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.TropicalFish;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Set;

/**
 * Manages fish breeding pairs and breeding logic
 */
public class BreedingManager {

    private final FishMatingPlugin plugin;
    private final Set<BreedingPair> activeBreedingPairs;
    private BukkitTask breedingCheckTask;

    public BreedingManager(FishMatingPlugin plugin) {
        this.plugin = plugin;
        this.activeBreedingPairs = ConcurrentHashMap.newKeySet();
        startBreedingCheckTask();
    }

    /**
     * Starts the periodic task that checks for breeding opportunities
     */
    private void startBreedingCheckTask() {
        breedingCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                checkForBreedingOpportunities();
                cleanupInvalidPairs();
            } catch (Exception e) {
                plugin.getLogger().warning("Error in breeding check task: " + e.getMessage());
            }
        }, 20L, 20L); // Run every second
    }

    /**
     * Checks for breeding opportunities among the tracked breeding-ready fish.
     *
     * <p>Rather than scan every entity in every world, this iterates the fish already
     * tracked by {@link FishManager} (populated via spawn / chunk-load events) and
     * buckets the breeding-ready, unpaired ones by world and type. Pairing is then
     * attempted only within each single-world, single-type bucket.
     */
    private void checkForBreedingOpportunities() {
        ConfigManager config = plugin.getConfigManager();

        try {
            Map<World, Map<EntityType, List<FishData>>> candidates = new HashMap<>();

            for (FishData fishData : plugin.getFishManager().getTrackedFish()) {
                if (!fishData.isBreedingReady()) {
                    continue;
                }
                Entity entity = fishData.getEntity();
                if (!entity.isValid() || entity.isDead() || isInBreedingPair(entity)) {
                    continue;
                }
                candidates
                        .computeIfAbsent(entity.getWorld(), w -> new HashMap<>())
                        .computeIfAbsent(entity.getType(), t -> new ArrayList<>())
                        .add(fishData);
            }

            for (Map<EntityType, List<FishData>> byType : candidates.values()) {
                for (List<FishData> sameWorldSameType : byType.values()) {
                    if (sameWorldSameType.size() >= 2) {
                        checkBreedingPairs(sameWorldSameType, config);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking breeding opportunities: " + e.getMessage());
        }
    }


    /**
     * Checks for breeding pairs among breeding-ready fish of the same type
     * @param breedingReadyFish List of breeding-ready fish
     * @param config Configuration manager
     */
    private void checkBreedingPairs(List<FishData> breedingReadyFish, ConfigManager config) {
        double radius = config.getDetectionRadius();

        // Track fish already paired this cycle instead of mutating the list while
        // iterating it by index (which could shift indices and skip fish).
        Set<FishData> paired = new HashSet<>();

        for (int i = 0; i < breedingReadyFish.size(); i++) {
            FishData fish1Data = breedingReadyFish.get(i);
            if (paired.contains(fish1Data)) {
                continue;
            }
            Entity fish1 = fish1Data.getEntity();

            for (int j = i + 1; j < breedingReadyFish.size(); j++) {
                FishData fish2Data = breedingReadyFish.get(j);
                if (paired.contains(fish2Data)) {
                    continue;
                }
                Entity fish2 = fish2Data.getEntity();

                // distance() throws across worlds; only pair fish in the same world.
                if (!fish1.getWorld().equals(fish2.getWorld())) {
                    continue;
                }

                // Check if fish are within breeding range
                if (fish1.getLocation().distance(fish2.getLocation()) <= radius) {
                    // Create breeding pair and initiate breeding
                    BreedingPair pair = new BreedingPair(fish1, fish2);
                    activeBreedingPairs.add(pair);

                    // Start breeding process
                    initiateBreeding(fish1Data, fish2Data, pair);

                    // Both fish are now spoken for this cycle
                    paired.add(fish1Data);
                    paired.add(fish2Data);
                    break;
                }
            }
        }
    }

    /**
     * Initiates the breeding process for a pair of fish
     * @param fish1Data First fish data
     * @param fish2Data Second fish data
     * @param pair The breeding pair
     */
    private void initiateBreeding(FishData fish1Data, FishData fish2Data, BreedingPair pair) {
        Entity fish1 = fish1Data.getEntity();
        Entity fish2 = fish2Data.getEntity();

        // Show breeding particles
        if (plugin.getConfigManager().isParticlesEnabled()) {
            ParticleUtils.showBreedingParticles(fish1.getLocation());
            ParticleUtils.showBreedingParticles(fish2.getLocation());
        }

        // Schedule baby spawning after a short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pair.isValid()) {
                spawnBabyFish(fish1, fish2);
                completeBreeding(fish1Data, fish2Data, pair);
            }
        }, 40L); // 2 seconds delay
    }

    /**
     * Spawns a baby fish between two parent fish
     * @param parent1 First parent fish
     * @param parent2 Second parent fish
     */
    private void spawnBabyFish(Entity parent1, Entity parent2) {
        try {
            // Calculate spawn location (midpoint between parents)
            Location spawnLocation = parent1.getLocation().clone()
                    .add(parent2.getLocation().toVector())
                    .multiply(0.5);

            // Spawn baby fish
            Entity baby = parent1.getWorld().spawnEntity(spawnLocation, parent1.getType());

            // Inherit appearance for species with visual variants (e.g. tropical fish).
            inheritAppearance(baby, parent1, parent2);

            // Put the newborn on the same breeding cooldown as its parents. Without this
            // a freshly bred fish could immediately consume a seed and breed again,
            // letting a player chain births far faster than intended. getFishData()
            // creates (or returns the existing) tracking record for the baby.
            plugin.getFishManager().getFishData(baby).setLastBreedingTime();

            // Set baby properties if supported. The four default fish are not Ageable in
            // current Minecraft, so this branch is inert for them today; it is kept
            // deliberately so offspring are correctly aged-down if a future version makes
            // these species Ageable, or if an admin maps an Ageable mob via config.
            if (baby instanceof org.bukkit.entity.Ageable) {
                ((org.bukkit.entity.Ageable) baby).setBaby();
            }

            // Show birth particles
            if (plugin.getConfigManager().isParticlesEnabled()) {
                ParticleUtils.showBirthParticles(spawnLocation);
            }

            // Award breeding experience at the baby, mirroring vanilla mob breeding
            // (a random amount up to the configured, vanilla-capped maximum).
            awardBreedingExperience(spawnLocation);

            plugin.getLogger().fine(() -> "Baby fish spawned at " + spawnLocation);

        } catch (Exception e) {
            plugin.getLogger().warning("Error spawning baby fish: " + e.getMessage());
        }
    }

    /**
     * Drops experience at the given location for a successful breed, matching vanilla
     * mob breeding which awards a random 1-7 XP. The maximum is configurable but capped
     * at vanilla; a configured maximum of 0 disables the reward entirely.
     *
     * @param location Where to spawn the experience orb (the baby's location)
     */
    private void awardBreedingExperience(Location location) {
        int max = plugin.getConfigManager().getBreedingExperience();
        if (max <= 0) {
            return;
        }
        int amount = ThreadLocalRandom.current().nextInt(max) + 1; // 1..max inclusive
        location.getWorld().spawn(location, ExperienceOrb.class, orb -> orb.setExperience(amount));
    }

    /**
     * Copies visual variant data from one randomly chosen parent onto the baby, for
     * species that have appearance variants. Tropical fish are the only mapped species
     * with variants — a pattern plus body and pattern colours; without this the server
     * would assign the baby a random, unrelated variant. Other fish have no variant
     * and are left unchanged.
     *
     * @param baby The newly spawned baby entity
     * @param parent1 The first parent
     * @param parent2 The second parent
     */
    private void inheritAppearance(Entity baby, Entity parent1, Entity parent2) {
        if (baby instanceof TropicalFish babyFish
                && parent1 instanceof TropicalFish firstParent
                && parent2 instanceof TropicalFish secondParent) {
            // Offspring take one parent's full variant at random, matching Minecraft's
            // convention for variant inheritance (vanilla tropical fish do not breed).
            TropicalFish source = ThreadLocalRandom.current().nextBoolean() ? firstParent : secondParent;
            babyFish.setPattern(source.getPattern());
            babyFish.setBodyColor(source.getBodyColor());
            babyFish.setPatternColor(source.getPatternColor());
        }
    }

    /**
     * Completes the breeding process for a pair of fish
     * @param fish1Data First fish data
     * @param fish2Data Second fish data
     * @param pair The breeding pair
     */
    private void completeBreeding(FishData fish1Data, FishData fish2Data, BreedingPair pair) {
        // Set breeding cooldown for both fish
        fish1Data.setLastBreedingTime();
        fish2Data.setLastBreedingTime();

        // Remove from active breeding pairs
        activeBreedingPairs.remove(pair);

        plugin.getLogger().fine("Breeding completed for pair");
    }

    /**
     * Checks if a fish is currently in a breeding pair
     * @param fish The fish entity to check
     * @return True if the fish is in an active breeding pair
     */
    private boolean isInBreedingPair(Entity fish) {
        return activeBreedingPairs.stream()
                .anyMatch(pair -> pair.contains(fish));
    }

    /**
     * Removes invalid breeding pairs from the active set
     */
    private void cleanupInvalidPairs() {
        activeBreedingPairs.removeIf(pair -> !pair.isValid());
    }

    /**
     * Shuts down the breeding manager
     */
    public void shutdown() {
        if (breedingCheckTask != null && !breedingCheckTask.isCancelled()) {
            breedingCheckTask.cancel();
        }
        activeBreedingPairs.clear();
    }

    /**
     * Gets the number of active breeding pairs
     * @return The number of active breeding pairs
     */
    public int getActiveBreedingPairCount() {
        return activeBreedingPairs.size();
    }
}
