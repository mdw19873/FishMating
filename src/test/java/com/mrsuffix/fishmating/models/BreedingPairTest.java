package com.mrsuffix.fishmating.models;

import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.SimpleEntityMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BreedingPair}.
 *
 * <p>Both fish are real MockBukkit {@code SimpleEntityMock} instances.
 * {@code contains(...)} relies on {@code Entity.equals}, which MockBukkit implements
 * as a UUID comparison — exactly the semantics this class needs. Validity is driven
 * by {@code Entity#remove()}, which flips the entity to invalid/dead.
 */
class BreedingPairTest {

    private ServerMock server;
    private Entity fish1;
    private Entity fish2;
    private Entity stranger;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        fish1 = new SimpleEntityMock(server);
        fish2 = new SimpleEntityMock(server);
        stranger = new SimpleEntityMock(server);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("The pair exposes both fish and a creation timestamp")
    void exposesMembersAndCreationTime() {
        BreedingPair pair = new BreedingPair(fish1, fish2);

        assertSame(fish1, pair.getFish1());
        assertSame(fish2, pair.getFish2());
        assertTrue(pair.getCreationTime() > 0);
    }

    @Test
    @DisplayName("contains() is true for either member and false for an outsider")
    void containsChecksMembership() {
        BreedingPair pair = new BreedingPair(fish1, fish2);

        assertTrue(pair.contains(fish1));
        assertTrue(pair.contains(fish2));
        assertFalse(pair.contains(stranger));
    }

    @Test
    @DisplayName("A pair is valid when both fish are valid and alive")
    void validWhenBothAlive() {
        BreedingPair pair = new BreedingPair(fish1, fish2);

        assertTrue(pair.isValid());
    }

    @Test
    @DisplayName("A pair is invalid once its first fish is removed")
    void invalidWhenFirstFishRemoved() {
        BreedingPair pair = new BreedingPair(fish1, fish2);

        fish1.remove();

        assertFalse(pair.isValid());
    }

    @Test
    @DisplayName("A pair is invalid once its second fish is removed")
    void invalidWhenSecondFishRemoved() {
        BreedingPair pair = new BreedingPair(fish1, fish2);

        fish2.remove();

        assertFalse(pair.isValid());
    }
}
