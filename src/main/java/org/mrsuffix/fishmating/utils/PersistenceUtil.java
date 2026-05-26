package com.mrsuffix.fishmating.utils;

import io.papermc.paper.entity.Bucketable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Lets bred fish inherit "won't despawn" persistence from their parents, mirroring how a
 * fish placed from a bucket no longer despawns.
 *
 * <p>Despawning is governed by two independent flags. A name-tagged / API-persisted mob sets
 * {@code PersistenceRequired}, which {@link LivingEntity#getRemoveWhenFarAway()} reflects
 * (returning {@code false}). A <em>bucketed</em> fish, however, won't despawn because of its
 * {@code FromBucket} flag — and {@code getRemoveWhenFarAway()} does <strong>not</strong> reflect
 * that (it reads only {@code PersistenceRequired}), so a bucket-placed fish reports
 * {@code getRemoveWhenFarAway() == true} despite never despawning. {@link #persists(Entity)}
 * therefore also checks {@link Bucketable#isFromBucket()} (Bug #7). To make a newborn persist we
 * set {@code removeWhenFarAway} to {@code false} (i.e. {@code PersistenceRequired}); the baby
 * isn't from a bucket, so that is the correct lever for it.
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

    /**
     * Pure persistence decision from the two independent flags, so it's unit-testable without
     * the live (MockBukkit-unimplemented) entity reads.
     *
     * @param removeWhenFarAway value of {@link LivingEntity#getRemoveWhenFarAway()} (driven by
     *                          {@code PersistenceRequired})
     * @param fromBucket        whether the entity came from a bucket ({@code FromBucket})
     * @return whether the entity won't despawn — i.e. {@code PersistenceRequired} is set OR it is
     *         from a bucket
     */
    static boolean persistsFromState(boolean removeWhenFarAway, boolean fromBucket) {
        return !removeWhenFarAway || fromBucket;
    }

    /** @return whether the entity persists (won't despawn); {@code false} for non-living. */
    public static boolean persists(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        boolean fromBucket = entity instanceof Bucketable bucketable && bucketable.isFromBucket();
        return persistsFromState(living.getRemoveWhenFarAway(), fromBucket);
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
