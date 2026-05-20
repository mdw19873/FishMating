package com.mrsuffix.fishmating.models;

import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.SimpleEntityMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FishData}.
 *
 * <p>{@code FishData} wraps a Bukkit {@link Entity}; rather than mock Paper's deep
 * {@code Entity} interface graph, these tests use MockBukkit's real {@code
 * SimpleEntityMock}. Time-based logic is verified with boundary values
 * (cooldown/timeout of 0) instead of {@code Thread.sleep}, keeping the tests fast
 * and deterministic.
 */
class FishDataTest {

    private ServerMock server;
    private Entity entity;
    private Entity seed;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        entity = new SimpleEntityMock(server);
        seed = new SimpleEntityMock(server);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("A new FishData starts not-ready, with no timestamps or target seed")
    void newFishDataHasDefaults() {
        FishData data = new FishData(entity);

        assertSame(entity, data.getEntity());
        assertFalse(data.isBreedingReady());
        assertEquals(0, data.getBreedingReadyTime());
        assertEquals(0, data.getLastBreedingTime());
        assertNull(data.getTargetSeed());
    }

    @Test
    @DisplayName("Marking breeding-ready records a timestamp; clearing it resets to zero")
    void setBreedingReadyTogglesTimestamp() {
        FishData data = new FishData(entity);

        data.setBreedingReady(true);
        assertTrue(data.isBreedingReady());
        assertTrue(data.getBreedingReadyTime() > 0);

        data.setBreedingReady(false);
        assertFalse(data.isBreedingReady());
        assertEquals(0, data.getBreedingReadyTime());
    }

    @Test
    @DisplayName("Recording a breeding clears readiness and stamps the last-breeding time")
    void setLastBreedingTimeClearsReadiness() {
        FishData data = new FishData(entity);
        data.setBreedingReady(true);

        data.setLastBreedingTime();

        assertTrue(data.getLastBreedingTime() > 0);
        assertFalse(data.isBreedingReady());
        assertEquals(0, data.getBreedingReadyTime());
    }

    @Test
    @DisplayName("A fish that has never bred can always breed")
    void canBreedWhenNeverBred() {
        FishData data = new FishData(entity);

        assertTrue(data.canBreed(3));
    }

    @Test
    @DisplayName("With a zero-minute cooldown a fish can breed again immediately")
    void canBreedWithZeroCooldown() {
        FishData data = new FishData(entity);
        data.setLastBreedingTime();

        assertTrue(data.canBreed(0));
    }

    @Test
    @DisplayName("A fish that just bred is still on cooldown for a non-zero window")
    void cannotBreedDuringCooldown() {
        FishData data = new FishData(entity);
        data.setLastBreedingTime();

        assertFalse(data.canBreed(60));
    }

    @Test
    @DisplayName("A not-ready fish never reports a breeding timeout")
    void notReadyNeverTimesOut() {
        FishData data = new FishData(entity);

        assertFalse(data.hasBreedingTimedOut(30));
    }

    @Test
    @DisplayName("A ready fish with a zero-second timeout is immediately timed out")
    void readyTimesOutWithZeroTimeout() {
        FishData data = new FishData(entity);
        data.setBreedingReady(true);

        assertTrue(data.hasBreedingTimedOut(0));
    }

    @Test
    @DisplayName("A ready fish has not timed out within a generous window")
    void readyDoesNotTimeOutImmediately() {
        FishData data = new FishData(entity);
        data.setBreedingReady(true);

        assertFalse(data.hasBreedingTimedOut(60));
    }

    @Test
    @DisplayName("The target seed can be set and read back")
    void targetSeedRoundTrips() {
        FishData data = new FishData(entity);

        data.setTargetSeed(seed);

        assertSame(seed, data.getTargetSeed());
    }
}
