package com.mrsuffix.fishmating.managers;

import com.mrsuffix.fishmating.FishMatingPlugin;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TropicalFish;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link BreedingManager}'s pairing logic, driven by ticking
 * MockBukkit's scheduler so the real periodic breeding-check task runs.
 *
 * <p>The breeding-check task first fires at tick 20; a single {@code performTicks(20)}
 * runs it exactly once. Baby spawning / pair completion is scheduled 40 ticks later,
 * so the freshly-formed pairs are still active when asserted.
 */
class BreedingManagerTest {

    private ServerMock server;
    private FishMatingPlugin plugin;
    private BreedingManager breedingManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(FishMatingPlugin.class);
        breedingManager = plugin.getBreedingManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Spawns a salmon and marks it breeding-ready in the FishManager. */
    private Entity spawnReadySalmon(WorldMock world, double x, double z) {
        Entity salmon = world.spawnEntity(new Location(world, x, 64, z), EntityType.SALMON);
        plugin.getFishManager().getFishData(salmon).setBreedingReady(true);
        return salmon;
    }

    private void runBreedingCheck() {
        server.getScheduler().performTicks(20L);
    }

    @Test
    @DisplayName("Two breeding-ready fish within range form one pair")
    void twoNearbyReadyFishPair() {
        WorldMock world = server.addSimpleWorld("w");
        spawnReadySalmon(world, 0, 0);
        spawnReadySalmon(world, 2, 0);

        runBreedingCheck();

        assertEquals(1, breedingManager.getActiveBreedingPairCount());
    }

    @Test
    @DisplayName("Breeding-ready fish beyond the detection radius do not pair")
    void distantReadyFishDoNotPair() {
        WorldMock world = server.addSimpleWorld("w");
        spawnReadySalmon(world, 0, 0);
        spawnReadySalmon(world, 20, 0);   // detection radius defaults to 5.0

        runBreedingCheck();

        assertEquals(0, breedingManager.getActiveBreedingPairCount());
    }

    @Test
    @DisplayName("Fish that are not breeding-ready do not pair")
    void notReadyFishDoNotPair() {
        WorldMock world = server.addSimpleWorld("w");
        world.spawnEntity(new Location(world, 0, 64, 0), EntityType.SALMON);
        world.spawnEntity(new Location(world, 2, 64, 0), EntityType.SALMON);

        runBreedingCheck();

        assertEquals(0, breedingManager.getActiveBreedingPairCount());
    }

    @Test
    @DisplayName("Bug #2: a fish in another world does not abort same-world pairing")
    void crossWorldFishDoesNotBlockPairing() {
        // The lone fish lives in the first-added world, so it is the first entity
        // examined. Before the fix, comparing it to a fish in another world threw an
        // IllegalArgumentException that aborted the entire breeding cycle.
        WorldMock world1 = server.addSimpleWorld("w1");
        WorldMock world2 = server.addSimpleWorld("w2");
        spawnReadySalmon(world1, 0, 0);
        spawnReadySalmon(world2, 0, 0);
        spawnReadySalmon(world2, 2, 0);

        runBreedingCheck();

        assertEquals(1, breedingManager.getActiveBreedingPairCount());
    }

    @Test
    @DisplayName("Bug #3: four nearby ready fish form exactly two pairs, none skipped")
    void fourNearbyReadyFishFormTwoPairs() {
        WorldMock world = server.addSimpleWorld("w");
        spawnReadySalmon(world, 0, 0);
        spawnReadySalmon(world, 1, 0);
        spawnReadySalmon(world, 2, 0);
        spawnReadySalmon(world, 3, 0);

        runBreedingCheck();

        assertEquals(2, breedingManager.getActiveBreedingPairCount());
    }

    @Test
    @DisplayName("A bred newborn is placed on the breeding cooldown")
    void newbornIsOnBreedingCooldown() {
        WorldMock world = server.addSimpleWorld("w");
        Entity p1 = spawnReadySalmon(world, 0, 0);
        Entity p2 = spawnReadySalmon(world, 2, 0);

        // Breeding check fires at tick 20 and spawns the baby 40 ticks later.
        server.getScheduler().performTicks(60L);

        Entity baby = world.getEntitiesByClass(org.bukkit.entity.Salmon.class).stream()
                .filter(f -> !f.getUniqueId().equals(p1.getUniqueId()))
                .filter(f -> !f.getUniqueId().equals(p2.getUniqueId()))
                .findFirst()
                .orElse(null);
        assertNotNull(baby, "expected a baby salmon to spawn");

        int cooldown = plugin.getConfigManager().getBreedingCooldownMinutes();
        assertFalse(plugin.getFishManager().getFishData(baby).canBreed(cooldown),
                "a freshly bred fish should be on cooldown, not immediately breedable");
    }

    @Test
    @DisplayName("A successful breed drops experience within the vanilla 1-7 range")
    void breedingAwardsExperience() {
        WorldMock world = server.addSimpleWorld("w");
        spawnReadySalmon(world, 0, 0);
        spawnReadySalmon(world, 2, 0);

        // Breeding check fires at tick 20 and spawns the baby (and XP) 40 ticks later.
        server.getScheduler().performTicks(60L);

        java.util.Collection<org.bukkit.entity.ExperienceOrb> orbs =
                world.getEntitiesByClass(org.bukkit.entity.ExperienceOrb.class);
        assertEquals(1, orbs.size(), "expected exactly one experience orb from the breed");

        int xp = orbs.iterator().next().getExperience();
        assertTrue(xp >= 1 && xp <= 7, "breeding XP should be within vanilla's 1-7, was " + xp);
    }

    @Test
    @DisplayName("A bred tropical fish baby inherits a parent's variant")
    void tropicalFishBabyInheritsParentVariant() {
        WorldMock world = server.addSimpleWorld("w");
        TropicalFish p1 = spawnReadyTropicalFish(world, 0, 0);
        TropicalFish p2 = spawnReadyTropicalFish(world, 2, 0);

        // Both parents share one distinctive variant so inheritance is deterministic
        // regardless of which parent is chosen as the source.
        for (TropicalFish parent : new TropicalFish[]{p1, p2}) {
            parent.setPattern(TropicalFish.Pattern.BETTY);
            parent.setBodyColor(DyeColor.RED);
            parent.setPatternColor(DyeColor.BLUE);
        }

        // Breeding check fires at tick 20 and schedules the baby 40 ticks later.
        server.getScheduler().performTicks(60L);

        TropicalFish baby = findBaby(world, p1, p2);
        assertNotNull(baby, "expected a baby tropical fish to spawn");
        assertEquals(TropicalFish.Pattern.BETTY, baby.getPattern());
        assertEquals(DyeColor.RED, baby.getBodyColor());
        assertEquals(DyeColor.BLUE, baby.getPatternColor());
    }

    private TropicalFish spawnReadyTropicalFish(WorldMock world, double x, double z) {
        TropicalFish fish = (TropicalFish) world.spawnEntity(
                new Location(world, x, 64, z), EntityType.TROPICAL_FISH);
        plugin.getFishManager().getFishData(fish).setBreedingReady(true);
        return fish;
    }

    private TropicalFish findBaby(WorldMock world, Entity parent1, Entity parent2) {
        return world.getEntitiesByClass(TropicalFish.class).stream()
                .filter(f -> !f.getUniqueId().equals(parent1.getUniqueId()))
                .filter(f -> !f.getUniqueId().equals(parent2.getUniqueId()))
                .findFirst()
                .orElse(null);
    }
}
