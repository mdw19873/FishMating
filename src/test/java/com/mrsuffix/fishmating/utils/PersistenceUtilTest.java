package com.mrsuffix.fishmating.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure cores of persistence inheritance —
 * {@link PersistenceUtil#shouldPersist(boolean, boolean, boolean)} and
 * {@link PersistenceUtil#persistsFromState(boolean, boolean)}. The live entity reads/writes
 * ({@link PersistenceUtil#persists} / {@link PersistenceUtil#inheritPersistence}, which call
 * {@code getRemoveWhenFarAway}/{@code isFromBucket}/{@code setRemoveWhenFarAway}) are
 * unimplemented in MockBukkit, so they are covered only by the floor/ceiling compile guards.
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

    @Test
    @DisplayName("persistsFromState: PersistenceRequired (removeWhenFarAway == false) means persists")
    void persistsWhenPersistenceRequired() {
        assertTrue(PersistenceUtil.persistsFromState(false, false));
        assertTrue(PersistenceUtil.persistsFromState(false, true));
    }

    @Test
    @DisplayName("Regression: a bucketed fish persists even though removeWhenFarAway reports true")
    void persistsWhenFromBucket() {
        // getRemoveWhenFarAway() is computed only from PersistenceRequired and ignores
        // FromBucket, so a bucket-placed fish reports removeWhenFarAway == true while still
        // never despawning. persists() must still count it (so its baby inherits persistence).
        assertTrue(PersistenceUtil.persistsFromState(true, true));
    }

    @Test
    @DisplayName("persistsFromState: a plain despawnable fish does not persist")
    void doesNotPersistWhenDespawnableAndNotBucketed() {
        assertFalse(PersistenceUtil.persistsFromState(true, false));
    }
}
