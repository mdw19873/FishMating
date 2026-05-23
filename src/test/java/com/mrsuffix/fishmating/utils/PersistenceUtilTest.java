package com.mrsuffix.fishmating.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PersistenceUtil#shouldPersist(boolean, boolean, boolean)}, the pure
 * core of persistence inheritance. The live {@code removeWhenFarAway} read/write
 * ({@link PersistenceUtil#persists} / {@link PersistenceUtil#inheritPersistence}) is
 * unimplemented in MockBukkit, so it is covered only by the floor/ceiling compile guards.
 *
 * <p>Rule: a baby persists only when the feature is enabled AND at least one parent persists.
 */
class PersistenceUtilTest {

    @Test
    @DisplayName("Disabled never persists, regardless of parents")
    void disabledNeverPersists() {
        assertFalse(PersistenceUtil.shouldPersist(false, false, false));
        assertFalse(PersistenceUtil.shouldPersist(false, true, false));
        assertFalse(PersistenceUtil.shouldPersist(false, false, true));
        assertFalse(PersistenceUtil.shouldPersist(false, true, true));
    }

    @Test
    @DisplayName("Enabled persists when either parent persists")
    void enabledInheritsFromEitherParent() {
        assertTrue(PersistenceUtil.shouldPersist(true, true, false));
        assertTrue(PersistenceUtil.shouldPersist(true, false, true));
        assertTrue(PersistenceUtil.shouldPersist(true, true, true));
    }

    @Test
    @DisplayName("Enabled but neither parent persists does not persist")
    void enabledWithNoPersistentParentDoesNotPersist() {
        assertFalse(PersistenceUtil.shouldPersist(true, false, false));
    }
}
