package com.mrsuffix.fishmating.utils;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Lets bred fish inherit "won't despawn" persistence from their parents, mirroring how a
 * fish placed from a bucket no longer despawns.
 *
 * <p>Despawning is controlled by {@link LivingEntity#getRemoveWhenFarAway()}: a fish that
 * <em>persists</em> (e.g. one placed from a bucket, which vanilla flags
 * {@code PersistenceRequired}) returns {@code false}. Setting it {@code false} on the
 * newborn makes the offspring persist too.
 *
 * <p>This is gated by config and only inherits — a baby persists only when a parent already
 * does — so ordinary wild-fish breeding stays despawnable and bounded (the anti-farm model);
 * only player-invested persistent lineages carry the trait forward.
 *
 * <p>The live {@code removeWhenFarAway} read/write is unimplemented in MockBukkit, so the
 * decision is split into the pure {@link #shouldPersist(boolean, boolean, boolean)} (unit
 * tested) while the entity access is covered by the floor/ceiling compile guards.
 */
public final class PersistenceUtil {

    private PersistenceUtil() {
    }

    /**
     * @param enabled          whether persistence inheritance is turned on in config
     * @param parent1Persists  whether the first parent already persists (won't despawn)
     * @param parent2Persists  whether the second parent already persists (won't despawn)
     * @return whether the newborn should be made persistent
     */
    public static boolean shouldPersist(boolean enabled, boolean parent1Persists, boolean parent2Persists) {
        return enabled && (parent1Persists || parent2Persists);
    }

    /** @return whether the entity persists (won't despawn); {@code false} for non-living. */
    public static boolean persists(Entity entity) {
        return entity instanceof LivingEntity living && !living.getRemoveWhenFarAway();
    }

    /**
     * Makes {@code baby} persist when enabled and at least one parent already persists.
     * No-op otherwise, and when {@code baby} is not a {@link LivingEntity}.
     *
     * @param baby    the newborn fish
     * @param parent1 the first parent
     * @param parent2 the second parent
     * @param enabled whether persistence inheritance is turned on in config
     */
    public static void inheritPersistence(Entity baby, Entity parent1, Entity parent2, boolean enabled) {
        if (!enabled || !(baby instanceof LivingEntity babyLiving)) {
            return;
        }
        if (shouldPersist(true, persists(parent1), persists(parent2))) {
            babyLiving.setRemoveWhenFarAway(false);
        }
    }
}
