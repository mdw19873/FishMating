package com.mrsuffix.fishmating.managers;

import com.mrsuffix.fishmating.FishMatingPlugin;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link ConfigManager} driven by the bundled default
 * {@code config.yml}, loaded through a MockBukkit-hosted plugin instance.
 */
class ConfigManagerTest {

    private FishMatingPlugin plugin;
    private ConfigManager config;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(FishMatingPlugin.class);
        config = plugin.getConfigManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Default numeric/boolean settings match the bundled config.yml")
    void loadsDefaultSettings() {
        assertEquals(5.0, config.getDetectionRadius());
        assertEquals(30, config.getBreedingTimeoutSeconds());
        assertEquals(3, config.getBreedingCooldownMinutes());
        assertTrue(config.isParticlesEnabled());
        assertEquals(5, config.getParticleCount());
    }

    @Test
    @DisplayName("Each configured fish maps to its seed material")
    void mapsFishToSeed() {
        assertEquals(Material.WHEAT_SEEDS, config.getSeedForFish(EntityType.SALMON));
        assertEquals(Material.PUMPKIN_SEEDS, config.getSeedForFish(EntityType.COD));
        assertEquals(Material.MELON_SEEDS, config.getSeedForFish(EntityType.PUFFERFISH));
        assertEquals(Material.BEETROOT_SEEDS, config.getSeedForFish(EntityType.TROPICAL_FISH));
    }

    @Test
    @DisplayName("Reverse lookup resolves a seed material back to its fish type")
    void mapsSeedToFish() {
        assertEquals(EntityType.SALMON, config.getFishForSeed(Material.WHEAT_SEEDS));
        assertNull(config.getFishForSeed(Material.STONE));
    }

    @Test
    @DisplayName("isBreedingSeed() recognises only configured seed materials")
    void recognisesBreedingSeeds() {
        assertTrue(config.isBreedingSeed(Material.WHEAT_SEEDS));
        assertFalse(config.isBreedingSeed(Material.STONE));
    }

    @Test
    @DisplayName("getFishSeedMappings() returns a defensive copy")
    void mappingsCopyIsDefensive() {
        int original = config.getFishSeedMappings().size();
        config.getFishSeedMappings().clear();

        assertEquals(original, config.getFishSeedMappings().size());
    }
}
