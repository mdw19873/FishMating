package com.mrsuffix.fishmating.managers;

import com.mrsuffix.fishmating.FishMatingPlugin;
import com.mrsuffix.fishmating.models.FishData;
import com.mrsuffix.fishmating.utils.ScaleUtil;
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

        // MockBukkit doesn't implement Item#getThrower, so disable the player-thrown
        // requirement for these mechanics tests; with it off the gate short-circuits and
        // getThrower is never called. (Default is true in production config.)
        plugin.getConfig().set("advanced.require-player-thrown-seeds", false);
        plugin.saveConfig();
        plugin.getConfigManager().loadConfiguration();
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
    @DisplayName("grownScale steps toward full size and never overshoots 1.0")
    void grownScaleStepsAndCaps() {
        // 5-minute growth at the 10-tick update period => 600 updates; one step is small.
        double oneStep = FishManager.grownScale(0.5, 0.5, 5, 10L);
        assertTrue(oneStep > 0.5 && oneStep < 0.6, "one step should nudge up slightly: " + oneStep);
        // A step that would cross 1.0 is capped, and a full-grown fish stays at 1.0.
        assertEquals(1.0, FishManager.grownScale(0.9999, 0.5, 5, 10L));
        assertEquals(1.0, FishManager.grownScale(1.0, 0.5, 5, 10L));
    }

    @Test
    @DisplayName("isFullGrown treats near-1.0 scales as adult and smaller ones as juvenile")
    void isFullGrownThreshold() {
        assertTrue(ScaleUtil.isFullGrown(1.0));
        assertTrue(ScaleUtil.isFullGrown(0.999));
        assertFalse(ScaleUtil.isFullGrown(0.5));
        assertFalse(ScaleUtil.isFullGrown(0.99));
    }

    @Test
    @DisplayName("With require-player-within set, a fish won't eat a seed if no player is near")
    void seekingBlockedWithoutNearbyPlayer() {
        plugin.getConfig().set("advanced.require-player-within", 10.0);
        plugin.saveConfig();
        plugin.getConfigManager().loadConfiguration();

        waterAt(0, 64, 0);
        dropSeed(Material.WHEAT_SEEDS, 3, 0, 64, 0);
        Entity salmon = trackedSalmon(0, 64, 0);

        runFishUpdate();

        assertFalse(fishManager.getFishData(salmon).isBreedingReady(),
                "with no nearby player, the fish should not seek or eat the seed");
    }

    @Test
    @DisplayName("With require-player-within set, a nearby player lets the fish eat the seed")
    void seekingAllowedWithNearbyPlayer() {
        plugin.getConfig().set("advanced.require-player-within", 10.0);
        plugin.saveConfig();
        plugin.getConfigManager().loadConfiguration();

        waterAt(0, 64, 0);
        dropSeed(Material.WHEAT_SEEDS, 3, 0, 64, 0);
        Entity salmon = trackedSalmon(0, 64, 0);
        server.addPlayer().teleport(new Location(world, 0, 64, 0));

        runFishUpdate();

        assertTrue(fishManager.getFishData(salmon).isBreedingReady(),
                "with a nearby player, the fish should eat the seed and become breeding-ready");
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

    // ----- event-driven attraction (attractFishToSeed) ----------------------------

    @Test
    @DisplayName("attractFishToSeed assigns the seed as the target of an eligible nearby fish")
    void attractAssignsTargetToEligibleFish() {
        waterAt(0, 64, 0);
        Item seed = dropSeed(Material.WHEAT_SEEDS, 1, 0, 64, 0);
        Entity salmon = trackedSalmon(0, 64, 0);

        fishManager.attractFishToSeed(seed);

        assertSame(seed, fishManager.getFishData(salmon).getTargetSeed());
    }

    @Test
    @DisplayName("attractFishToSeed ignores a fish that is not tracked (beyond the cap)")
    void attractIgnoresUntrackedFish() {
        waterAt(0, 64, 0);
        Item seed = dropSeed(Material.WHEAT_SEEDS, 1, 0, 64, 0);
        Entity salmon = world.spawnEntity(new Location(world, 0, 64, 0), EntityType.SALMON);
        fishManager.removeFishData(salmon); // make it untracked
        assertTrue(fishManager.getTrackedFish().isEmpty());

        fishManager.attractFishToSeed(seed);

        // Attraction must neither target nor start tracking an untracked fish.
        assertTrue(fishManager.getTrackedFish().isEmpty(),
                "attraction should only ever touch already-tracked fish");
    }

    @Test
    @DisplayName("attractFishToSeed skips a fish that is already breeding-ready")
    void attractSkipsBreedingReadyFish() {
        waterAt(0, 64, 0);
        Item seed = dropSeed(Material.WHEAT_SEEDS, 1, 0, 64, 0);
        Entity salmon = trackedSalmon(0, 64, 0);
        fishManager.getFishData(salmon).setBreedingReady(true);

        fishManager.attractFishToSeed(seed);

        assertNull(fishManager.getFishData(salmon).getTargetSeed());
    }

    @Test
    @DisplayName("attractFishToSeed skips a fish still on breeding cooldown")
    void attractSkipsFishOnCooldown() {
        waterAt(0, 64, 0);
        Item seed = dropSeed(Material.WHEAT_SEEDS, 1, 0, 64, 0);
        Entity salmon = trackedSalmon(0, 64, 0);
        fishManager.getFishData(salmon).setLastBreedingTime(); // just bred -> on cooldown

        fishManager.attractFishToSeed(seed);

        assertNull(fishManager.getFishData(salmon).getTargetSeed());
    }

    @Test
    @DisplayName("attractFishToSeed does not override a fish's existing valid target")
    void attractDoesNotOverrideExistingTarget() {
        waterAt(0, 64, 0);
        Item first = dropSeed(Material.WHEAT_SEEDS, 1, 0, 64, 0);
        Item second = dropSeed(Material.WHEAT_SEEDS, 1, 0, 64, 0);
        Entity salmon = trackedSalmon(0, 64, 0);

        fishManager.attractFishToSeed(first);
        fishManager.attractFishToSeed(second);

        assertSame(first, fishManager.getFishData(salmon).getTargetSeed(),
                "a fish should commit to its first seed");
    }

    @Test
    @DisplayName("The per-tick loop no longer polls: a seed never announced via event is ignored")
    void updateLoopDoesNotPollForSeeds() {
        // Unregister all plugin listeners so dropping the seed fires no attraction; only the
        // fish-update task keeps running. If the loop still scanned for seeds the adjacent
        // seed would be eaten — it must not be (discovery is now push-only via ItemDropListener).
        org.bukkit.event.HandlerList.unregisterAll(plugin);

        waterAt(0, 64, 0);
        dropSeed(Material.WHEAT_SEEDS, 1, 0, 64, 0);
        Entity salmon = trackedSalmon(0, 64, 0);

        runFishUpdate();

        FishData data = fishManager.getFishData(salmon);
        assertNull(data.getTargetSeed(), "the loop must not discover seeds on its own");
        assertFalse(data.isBreedingReady());
    }

    // ----- transition rescan safety net -------------------------------------------

    @Test
    @DisplayName("Transition rescan: becoming eligible (player arrives) re-acquires a lingering seed")
    void transitionRescanOnRisingEligibility() {
        plugin.getConfig().set("advanced.require-player-within", 10.0);
        plugin.saveConfig();
        plugin.getConfigManager().loadConfiguration();

        waterAt(0, 64, 0);
        dropSeed(Material.WHEAT_SEEDS, 1, 0, 64, 0); // seed present, but no player yet
        Entity salmon = trackedSalmon(0, 64, 0);

        runFishUpdate(); // not eligible (no player): wasEligible flips false, seed untouched
        assertFalse(fishManager.getFishData(salmon).isBreedingReady());

        server.addPlayer().teleport(new Location(world, 0, 64, 0));
        runFishUpdate(); // rising edge -> one-shot rescan finds the lingering seed
        runFishUpdate(); // and the fish reaches/consumes it

        assertTrue(fishManager.getFishData(salmon).isBreedingReady(),
                "a fish that becomes eligible next to a lingering seed should re-acquire it");
    }

    @Test
    @DisplayName("Transition rescan: a fish whose target seed vanishes re-acquires another nearby seed")
    void transitionRescanOnLostTarget() {
        // No event-driven attraction: drive the target assignment and loss manually.
        org.bukkit.event.HandlerList.unregisterAll(plugin);

        waterAt(0, 64, 0);
        Item eaten = dropSeed(Material.WHEAT_SEEDS, 1, 0, 64, 0);
        Item other = dropSeed(Material.WHEAT_SEEDS, 1, 0, 64, 0);
        Entity salmon = trackedSalmon(0, 64, 0);
        FishData data = fishManager.getFishData(salmon);
        data.setTargetSeed(eaten);
        eaten.remove(); // another fish "ate" it -> target now invalid

        runFishUpdate(); // clears the dead target -> lostTarget -> rescan picks the other seed

        assertSame(other, data.getTargetSeed(),
                "after losing its target the fish should re-acquire the other nearby seed");
    }
}
