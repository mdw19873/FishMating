package com.mrsuffix.fishmating.integrations;

/**
 * Pure decision logic for the WorldGuard breeding gate, free of any WorldGuard types so it
 * can be unit-tested without WorldGuard on the classpath (MockBukkit never has it).
 *
 * <p>{@link WorldGuardHook} maps the live region query down to the three booleans below and
 * delegates here; the live {@code StateFlag.State} mapping is covered only by the floor/
 * ceiling compile guards.
 */
public final class BreedingFlagDecision {

    private BreedingFlagDecision() {
    }

    /**
     * @param integrationEnabled whether {@code advanced.worldguard-integration} is on
     * @param worldGuardPresent  whether WorldGuard is installed and the flag is available
     * @param explicitlyDenied   {@code true} only when the region query returned {@code DENY}
     * @return {@code true} if breeding may proceed. The check is "not applicable" (always
     *         allows) when the integration is off or WorldGuard is absent; otherwise breeding
     *         is allowed unless a region explicitly denies it.
     */
    public static boolean allowed(boolean integrationEnabled,
                                  boolean worldGuardPresent,
                                  boolean explicitlyDenied) {
        if (!integrationEnabled || !worldGuardPresent) {
            return true; // not applicable
        }
        return !explicitlyDenied; // allow unless a region set the flag to DENY
    }
}
