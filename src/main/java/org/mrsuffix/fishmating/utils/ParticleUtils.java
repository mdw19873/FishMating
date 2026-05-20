package com.mrsuffix.fishmating.utils;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Utility class for particle effects.
 *
 * <p>Particles are referenced via {@link Particle} enum constants rather than string
 * names, so any particle that is renamed or removed in a future Minecraft version
 * fails at compile time instead of silently degrading at runtime.
 */
public class ParticleUtils {

    /**
     * Shows heart particles above a location (breeding ready state)
     * @param location The location to show particles
     * @param count The number of particles
     */
    public static void showHeartParticles(Location location, int count) {
        Location particleLocation = location.clone().add(0, 1.5, 0);
        spawnParticleSafely(Particle.HEART, particleLocation, count, 0.3, 0.3, 0.3, 0);
    }

    /**
     * Shows particles when a fish consumes a seed
     * @param location The location to show particles
     */
    public static void showConsumptionParticles(Location location) {
        spawnParticleSafely(Particle.HAPPY_VILLAGER, location, 5, 0.2, 0.2, 0.2, 0);
    }

    /**
     * Shows particles during the breeding process
     * @param location The location to show particles
     */
    public static void showBreedingParticles(Location location) {
        Location particleLocation = location.clone().add(0, 1, 0);
        spawnParticleSafely(Particle.HEART, particleLocation, 10, 0.5, 0.5, 0.5, 0);
    }

    /**
     * Shows particles when a baby fish is born
     * @param location The location to show particles
     */
    public static void showBirthParticles(Location location) {
        spawnParticleSafely(Particle.WITCH, location, 15, 0.5, 0.5, 0.5, 0.1);
        spawnParticleSafely(Particle.HAPPY_VILLAGER, location, 8, 0.3, 0.3, 0.3, 0);
    }

    /**
     * Safely spawns particles, guarding against a null world and swallowing any
     * runtime spawn error so particle effects never spam the console or interrupt
     * gameplay logic.
     *
     * @param particle The particle to spawn
     * @param location The location to spawn at
     * @param count The number of particles
     * @param offsetX X offset
     * @param offsetY Y offset
     * @param offsetZ Z offset
     * @param extra Extra data (e.g. particle speed)
     */
    private static void spawnParticleSafely(Particle particle, Location location, int count,
                                            double offsetX, double offsetY, double offsetZ, double extra) {
        try {
            World world = location.getWorld();
            if (world != null) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
            }
        } catch (Exception e) {
            // Silently handle particle errors to avoid console spam
        }
    }
}
