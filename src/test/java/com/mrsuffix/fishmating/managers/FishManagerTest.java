package com.mrsuffix.fishmating.managers;

import com.mrsuffix.fishmating.FishMatingPlugin;
import com.mrsuffix.fishmating.models.FishData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.SimpleEntityMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link FishManager}'s per-tick seek/consume loop, driven by
 * MockBukkit's scheduler. The fish-update task first fires at tick 20, so a single
 * {@code performTicks(20)} runs one update cycle.
 *
 * <p>MockBukkit does not simulate physics, so fish do not drift along their velocity;
 * a fish "reaches" a seed only when spawned within the 1.5-block consume range.
 */
class FishManagerTest {

    private ServerMock server;
    private FishMatingPlugin plugin;
    private FishManager fishManager;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(FishMatingPlugin.class);
        fishManager = plugin.getFishManager();
        world = server.addSimpleWorld("w");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void waterAt(double x, double y, double z) {
        world.getBlockAt(new Location(world, x, y, z)).setType(Material.WATER);
    }

    private Item dropSeed(Material seed, int amount, double x, double y, double z) {
        return world.dropItem(new Location(world, x, y, z), new ItemStack(seed, amount));
    }

    /** Spawns a salmon and registers it with the FishManager update loop. */
    private Entity trackedSalmon(double x, double y, double z) {
        Entity salmon = world.spawnEntity(new Location(world, x, y, z), EntityType.SALMON);
        fishManager.getFishData(salmon);
        return salmon;
    }

    private void runFishUpdate() {
        server.getScheduler().performTicks(20L);
    }

    @Test
    @DisplayName("Eating an adjacent in-water seed decrements the stack and sets breeding-ready")
    void consumesAdjacentSeedAndDecrementsStack() {
        waterAt(0, 64, 0);
        Item seed = dropSeed(Material.WHEAT_SEEDS, 3, 0, 64, 0);
        Entity salmon = trackedSalmon(0, 64, 0);

        runFishUpdate();

        assertTrue(fishManager.getFishData(salmon).isBreedingReady());
        assertFalse(seed.isDead());
        // Bug #5 regression guard: the stack must actually shrink (3 -> 2).
        assertEquals(2, seed.getItemStack().getAmount());
    }

    @Test
    @DisplayName("Eating the last seed in a stack removes the item")
    void consumingSingleSeedRemovesItem() {
        waterAt(0, 64, 0);
        Item seed = dropSeed(Material.WHEAT_SEEDS, 1, 0, 64, 0);
        Entity salmon = trackedSalmon(0, 64, 0);

        runFishUpdate();

        assertTrue(fishManager.getFishData(salmon).isBreedingReady());
        assertTrue(seed.isDead());
    }

    @Test
    @DisplayName("A fish targets a distant in-range seed and is given a finite velocity")
    void movesTowardDistantSeed() {
        waterAt(3, 64, 0);
        dropSeed(Material.WHEAT_SEEDS, 1, 3, 64, 0);
        Entity salmon = trackedSalmon(0, 64, 0);

        runFishUpdate();

        FishData data = fishManager.getFishData(salmon);
        assertFalse(data.isBreedingReady());          // 3 blocks away: not consumed
        assertNotNull(data.getTargetSeed());           // locked onto the seed
        assertTrue(Double.isFinite(salmon.getVelocity().getX()));
        assertTrue(salmon.getVelocity().lengthSquared() > 0);
    }

    @Test
    @DisplayName("A matching seed that is not in water is ignored")
    void ignoresSeedOutOfWater() {
        // No waterAt(...): the block stays AIR.
        dropSeed(Material.WHEAT_SEEDS, 1, 0, 64, 0);
        Entity salmon = trackedSalmon(0, 64, 0);

        runFishUpdate();

        FishData data = fishManager.getFishData(salmon);
        assertFalse(data.isBreedingReady());
        assertNull(data.getTargetSeed());
    }

    @Test
    @DisplayName("A seed of the wrong type for this fish is ignored")
    void ignoresNonMatchingSeed() {
        waterAt(0, 64, 0);
        // Salmon eat WHEAT_SEEDS; a pumpkin seed should be ignored.
        dropSeed(Material.PUMPKIN_SEEDS, 1, 0, 64, 0);
        Entity salmon = trackedSalmon(0, 64, 0);

        runFishUpdate();

        FishData data = fishManager.getFishData(salmon);
        assertFalse(data.isBreedingReady());
        assertNull(data.getTargetSeed());
    }

    @Test
    @DisplayName("getFishData is cached per entity; removeFishData clears it")
    void fishDataLifecycle() {
        Entity salmon = world.spawnEntity(new Location(world, 0, 64, 0), EntityType.SALMON);

        FishData first = fishManager.getFishData(salmon);
        first.setBreedingReady(true);
        assertSame(first, fishManager.getFishData(salmon));

        fishManager.removeFishData(salmon);
        assertFalse(fishManager.getFishData(salmon).isBreedingReady());
    }

    @Test
    @DisplayName("trackFish registers a mapped fish but ignores non-mapped entities")
    void trackFishOnlyTracksMappedTypes() {
        fishManager.trackFish(new SimpleEntityMock(server)); // type UNKNOWN -> ignored
        assertTrue(fishManager.getTrackedFish().isEmpty());

        Entity salmon = world.spawnEntity(new Location(world, 0, 64, 0), EntityType.SALMON);
        fishManager.removeFishData(salmon); // ignore any spawn-event tracking
        fishManager.trackFish(salmon);
        assertEquals(1, fishManager.getTrackedFish().size());
    }

    @Test
    @DisplayName("trackExistingFish picks up mapped fish already present in loaded worlds")
    void trackExistingFishScansLoadedWorlds() {
        world.spawnEntity(new Location(world, 0, 64, 0), EntityType.SALMON);
        world.spawnEntity(new Location(world, 5, 64, 0), EntityType.COD);

        fishManager.trackExistingFish();

        assertEquals(2, fishManager.getTrackedFish().size());
    }

    @Test
    @DisplayName("trackFish stops registering new fish once max-tracked-fish is reached")
    void trackFishRespectsMaxTrackedCap() {
        plugin.getConfig().set("advanced.max-tracked-fish", 2);
        plugin.saveConfig();
        plugin.getConfigManager().loadConfiguration();

        for (int i = 0; i < 3; i++) {
            Entity salmon = world.spawnEntity(new Location(world, i, 64, 0), EntityType.SALMON);
            fishManager.trackFish(salmon);
        }

        assertEquals(2, fishManager.getTrackedFish().size());
    }
}
