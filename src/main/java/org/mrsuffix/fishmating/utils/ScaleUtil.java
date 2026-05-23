package com.mrsuffix.fishmating.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;

/**
 * Reads and writes an entity's scale via the generic scale attribute
 * ({@code minecraft:scale}, added in 1.20.5), which shrinks/grows both the model and
 * the hitbox of any living entity.
 *
 * <p>The attribute is resolved from the registry by its namespaced key rather than the
 * {@code Attribute} constant on purpose: that constant was renamed across the 1.21 line
 * ({@code GENERIC_SCALE} → {@code SCALE}), so referencing it by name would fail to
 * compile against one end of the supported range. The key is stable, so this keeps a
 * single jar working across 1.21.x and 26.1.
 */
public final class ScaleUtil {

    private static final NamespacedKey SCALE_KEY = NamespacedKey.minecraft("scale");

    /** At/above this scale a fish counts as full-grown (small float-accumulation margin). */
    private static final double FULL_GROWN_SCALE = 0.999;

    private ScaleUtil() {
    }

    /** @return whether a scale value counts as full-grown. */
    public static boolean isFullGrown(double scale) {
        return scale >= FULL_GROWN_SCALE;
    }

    /** @return whether the entity is full-grown (or has no scale attribute, treated as adult). */
    public static boolean isFullGrown(Entity entity) {
        return isFullGrown(getScale(entity));
    }

    private static AttributeInstance scaleInstance(Entity entity) {
        if (!(entity instanceof Attributable attributable)) {
            return null;
        }
        Attribute scale = Registry.ATTRIBUTE.get(SCALE_KEY);
        return scale == null ? null : attributable.getAttribute(scale);
    }

    /**
     * @return the entity's current scale, or {@code 1.0} if the scale attribute is
     *         unavailable (e.g. a non-living entity or a platform without it).
     */
    public static double getScale(Entity entity) {
        AttributeInstance instance = scaleInstance(entity);
        return instance == null ? 1.0 : instance.getBaseValue();
    }

    /**
     * Sets the entity's scale base value. No-op if the scale attribute is unavailable.
     *
     * @param entity the entity to scale
     * @param value  the new scale (1.0 is normal adult size)
     */
    public static void setScale(Entity entity, double value) {
        AttributeInstance instance = scaleInstance(entity);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
