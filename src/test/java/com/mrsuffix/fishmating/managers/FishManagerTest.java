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
        // Regression guard: the stack must actually shrink (3 -> 2).
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
    @DisplayName("Regression: a seed thrown through the air still attracts fish once it lands in water")
    void seedEnteringWaterAfterSpawnAttractsFish() {
        // A thrown seed spawns at the player's hand — in AIR, not yet in the water it's
        // tossed into — so the spawn-time location is not water. The listener must keep
        // watching until the seed settles in water rather than checking only at spawn.
        Item seed = dropSeed(Material.WHEAT_SEEDS, 1, 0, 64, 0); // block here is still AIR
        Entity salmon = trackedSalmon(0, 64, 0);

        // While the seed is "in flight" (its block is air), no attraction happens.
        server.getScheduler().performTicks(10L);
        assertFalse(fishManager.getFishData(salmon).isBreedingReady(),
                "a seed not yet in water should not attract fish");

        // The seed comes to rest in water; the watcher must now attract the fish.
        waterAt(0, 64, 0);
        server.getScheduler().performTicks(40L);

        assertTrue(fishManager.getFishData(salmon).isBreedingReady(),
                "once the seed is in water the fish must seek and consume it");
    }

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

    // ----- held-seed temptation (herding) -----------------------------------------

    /** Adds a player at the given location holding {@code mainHand} in the main hand. */
    private org.bukkit.entity.Player playerHolding(Material mainHand, double x, double y, double z) {
        org.bukkit.entity.Player player = server.addPlayer();
        player.teleport(new Location(world, x, y, z));
        player.getInventory().setItemInMainHand(new ItemStack(mainHand));
        return player;
    }

    @Test
    @DisplayName("Holding a matching seed assigns a nearby adult fish a follow target (no breeding change)")
    void heldSeedTemptsAdultFish() {
        Entity salmon = trackedSalmon(0, 64, 0);
        org.bukkit.entity.Player player = playerHolding(Material.WHEAT_SEEDS, 2, 64, 0);

        runFishUpdate();

        FishData data = fishManager.getFishData(salmon);
        assertSame(player, data.getFollowTarget(), "fish should follow the seed-holding player");
        assertNull(data.getTargetSeed(), "temptation must not assign a thrown-seed target");
        assertFalse(data.isBreedingReady(), "temptation is herding only; it must not breed the fish");
    }

    @Test
    @DisplayName("An off-hand seed also tempts matching fish")
    void offHandSeedTempts() {
        Entity salmon = trackedSalmon(0, 64, 0);
        org.bukkit.entity.Player player = server.addPlayer();
        player.teleport(new Location(world, 2, 64, 0));
        player.getInventory().setItemInOffHand(new ItemStack(Material.WHEAT_SEEDS));

        runFishUpdate();

        assertSame(player, fishManager.getFishData(salmon).getFollowTarget());
    }

    @Test
    @DisplayName("A held seed of the wrong type for this fish does not tempt it")
    void nonMatchingHeldSeedDoesNotTempt() {
        Entity salmon = trackedSalmon(0, 64, 0); // salmon eat WHEAT_SEEDS
        playerHolding(Material.PUMPKIN_SEEDS, 2, 64, 0);

        runFishUpdate();

        assertNull(fishManager.getFishData(salmon).getFollowTarget());
    }

    @Test
    @DisplayName("A fish committed to a thrown seed ignores a tempting player (seed wins)")
    void thrownSeedWinsOverTemptation() {
        Entity salmon = trackedSalmon(0, 64, 0);
        Item seed = dropSeed(Material.WHEAT_SEEDS, 1, 3, 64, 0); // 3b away: targeted, not consumed
        fishManager.getFishData(salmon).setTargetSeed(seed);
        playerHolding(Material.WHEAT_SEEDS, 2, 64, 0);

        runFishUpdate();

        FishData data = fishManager.getFishData(salmon);
        assertSame(seed, data.getTargetSeed(), "the fish should stay committed to its thrown seed");
        assertNull(data.getFollowTarget(), "a seed-targeting fish must not be tempted");
    }

    @Test
    @DisplayName("A following fish loses interest when the player stops holding the seed")
    void losesInterestWhenSeedPutAway() {
        Entity salmon = trackedSalmon(0, 64, 0);
        org.bukkit.entity.Player player = playerHolding(Material.WHEAT_SEEDS, 2, 64, 0);

        runFishUpdate();
        assertSame(player, fishManager.getFishData(salmon).getFollowTarget());

        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        runFishUpdate();

        assertNull(fishManager.getFishData(salmon).getFollowTarget(),
                "fish should stop following once the seed is no longer held");
    }

    @Test
    @DisplayName("A following fish loses interest when the player leaves the temptation radius")
    void losesInterestWhenPlayerLeavesRadius() {
        Entity salmon = trackedSalmon(0, 64, 0);
        org.bukkit.entity.Player player = playerHolding(Material.WHEAT_SEEDS, 2, 64, 0);

        runFishUpdate();
        assertSame(player, fishManager.getFishData(salmon).getFollowTarget());

        player.teleport(new Location(world, 100, 64, 0)); // well beyond the 10-block radius
        runFishUpdate();

        assertNull(fishManager.getFishData(salmon).getFollowTarget());
    }

    @Test
    @DisplayName("A spectator holding a seed does not tempt fish")
    void spectatorDoesNotTempt() {
        Entity salmon = trackedSalmon(0, 64, 0);
        org.bukkit.entity.Player player = playerHolding(Material.WHEAT_SEEDS, 2, 64, 0);
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);

        runFishUpdate();

        assertNull(fishManager.getFishData(salmon).getFollowTarget());
    }

    @Test
    @DisplayName("With seed-temptation disabled, holding a seed tempts no fish")
    void temptationDisabledTemptsNothing() {
        plugin.getConfig().set("settings.seed-temptation", false);
        plugin.saveConfig();
        plugin.getConfigManager().loadConfiguration();

        Entity salmon = trackedSalmon(0, 64, 0);
        playerHolding(Material.WHEAT_SEEDS, 2, 64, 0);

        runFishUpdate();

        assertNull(fishManager.getFishData(salmon).getFollowTarget());
    }

    @Test
    @DisplayName("followVelocity mills (returns null) within the stop distance and dives toward a player beyond it")
    void followVelocityStopAndClamp() {
        // Within the 2.5-block stop distance horizontally: no push, the fish mills.
        assertNull(FishManager.followVelocity(0, 64, 0, 1, 64, 0, 2.5, 0.3));
        // Degenerate same position is also a no-op (avoids NaN from normalizing a zero vector).
        assertNull(FishManager.followVelocity(0, 64, 0, 0, 64, 0, 2.5, 0.3));

        // Player 10b away horizontally and 5b above: a finite velocity that never pushes up.
        org.bukkit.util.Vector v = FishManager.followVelocity(0, 64, 0, 0, 69, 10, 2.5, 0.3);
        assertNotNull(v);
        assertTrue(Double.isFinite(v.getX()) && Double.isFinite(v.getY()) && Double.isFinite(v.getZ()));
        assertTrue(v.getY() <= 0.0, "must never push the fish upward toward a player above water");
        assertTrue(v.getY() >= -0.1, "downward velocity is floored at -0.1");
        assertTrue(v.getZ() > 0, "horizontal velocity should aim toward the player");
    }
}
