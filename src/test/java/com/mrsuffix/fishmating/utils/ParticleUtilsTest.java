package com.mrsuffix.fishmating.utils;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link ParticleUtils}, asserting the exact {@link Particle} types emitted.
 *
 * <p>Guards against bug #1: the original code resolved particles by legacy string name
 * ({@code VILLAGER_HAPPY}, {@code SPELL_WITCH}) which no longer exist in 1.21, so it
 * silently fell back to {@code DRIPPING_HONEY}. These tests pin the rendered particle
 * for each effect, and would fail if a future version renames one of them.
 */
class ParticleUtilsTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("particles");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private List<Particle> spawnedParticles() {
        return world.getSpawnedParticles().stream()
                .map(p -> p.particle())
                .toList();
    }

    @Test
    @DisplayName("Heart particles use HEART")
    void heartParticlesUseHeart() {
        ParticleUtils.showHeartParticles(new Location(world, 0, 64, 0), 5);

        assertEquals(List.of(Particle.HEART), spawnedParticles());
    }

    @Test
    @DisplayName("Regression: consumption particles use HAPPY_VILLAGER (not a honey fallback)")
    void consumptionParticlesUseHappyVillager() {
        ParticleUtils.showConsumptionParticles(new Location(world, 0, 64, 0));

        assertEquals(List.of(Particle.HAPPY_VILLAGER), spawnedParticles());
    }

    @Test
    @DisplayName("Breeding particles use HEART")
    void breedingParticlesUseHeart() {
        ParticleUtils.showBreedingParticles(new Location(world, 0, 64, 0));

        assertEquals(List.of(Particle.HEART), spawnedParticles());
    }

    @Test
    @DisplayName("Regression: birth particles use WITCH then HAPPY_VILLAGER")
    void birthParticlesUseWitchAndHappyVillager() {
        ParticleUtils.showBirthParticles(new Location(world, 0, 64, 0));

        assertEquals(List.of(Particle.WITCH, Particle.HAPPY_VILLAGER), spawnedParticles());
    }

    @Test
    @DisplayName("Heart particle count honours the supplied argument")
    void heartParticleCountIsRespected() {
        ParticleUtils.showHeartParticles(new Location(world, 0, 64, 0), 7);

        assertEquals(7, world.getSpawnedParticles().get(0).count());
    }
}
