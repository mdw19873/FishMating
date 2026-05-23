package com.mrsuffix.fishmating.integrations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BreedingFlagDecision#allowed(boolean, boolean, boolean)}, the pure
 * core of the WorldGuard breeding gate. The live {@code StateFlag.State} → {@code denied}
 * mapping in {@link WorldGuardHook} is covered only by the floor/ceiling compile guards,
 * since WorldGuard is never on the MockBukkit classpath.
 *
 * <p>Semantics: the check only applies when the integration is enabled AND WorldGuard is
 * present; then breeding is allowed unless a region explicitly denies it.
 */
class BreedingFlagDecisionTest {

    @Test
    @DisplayName("Disabled integration always allows, regardless of WorldGuard or deny")
    void disabledAlwaysAllows() {
        assertTrue(BreedingFlagDecision.allowed(false, false, false));
        assertTrue(BreedingFlagDecision.allowed(false, false, true));
        assertTrue(BreedingFlagDecision.allowed(false, true, false));
        assertTrue(BreedingFlagDecision.allowed(false, true, true));
    }

    @Test
    @DisplayName("Enabled but WorldGuard absent always allows (clean no-op)")
    void enabledWithoutWorldGuardAllows() {
        assertTrue(BreedingFlagDecision.allowed(true, false, false));
        assertTrue(BreedingFlagDecision.allowed(true, false, true));
    }

    @Test
    @DisplayName("Enabled with WorldGuard present allows unless the region denies")
    void enabledWithWorldGuardRespectsDeny() {
        assertTrue(BreedingFlagDecision.allowed(true, true, false));
        assertFalse(BreedingFlagDecision.allowed(true, true, true));
    }
}
