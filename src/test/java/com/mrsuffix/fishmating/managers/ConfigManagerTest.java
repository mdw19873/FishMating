package com.mrsuffix.fishmating.managers;

import com.mrsuffix.fishmating.FishMatingPlugin;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

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
        assertEquals(5, config.getBreedingCooldownMinutes());
        assertEquals(7, config.getBreedingExperience());
        assertTrue(config.isParticlesEnabled());
        assertEquals(5, config.getParticleCount());
    }

    @Test
    @DisplayName("breeding-experience is clamped to the vanilla ceiling of 7")
    void breedingExperienceIsCappedAtVanilla() {
        plugin.getConfig().set("settings.breeding-experience", 100);
        plugin.saveConfig();

        config.loadConfiguration();

        assertEquals(7, config.getBreedingExperience());
    }

    @Test
    @DisplayName("A negative breeding-experience is clamped to 0 (disabled)")
    void negativeBreedingExperienceIsClampedToZero() {
        plugin.getConfig().set("settings.breeding-experience", -5);
        plugin.saveConfig();

        config.loadConfiguration();

        assertEquals(0, config.getBreedingExperience());
    }

    @Test
    @DisplayName("natural-growth defaults on, with baby-scale 0.5 and 10-minute growth")
    void naturalGrowthDefaults() {
        assertTrue(config.isNaturalGrowth());
        assertEquals(0.5, config.getBabyScale());
        assertEquals(10, config.getGrowthDurationMinutes());
    }

    @Test
    @DisplayName("require-player-thrown-seeds defaults to true")
    void requirePlayerThrownSeedsDefaultsTrue() {
        assertTrue(config.isRequirePlayerThrownSeeds());
    }

    @Test
    @DisplayName("require-player-within defaults to 0 (disabled)")
    void requirePlayerWithinDefaultsZero() {
        assertEquals(0.0, config.getRequirePlayerWithin());
    }

    @Test
    @DisplayName("worldguard-integration defaults to off")
    void worldGuardIntegrationDefaultsOff() {
        assertFalse(config.isWorldGuardIntegration());
    }

    @Test
    @DisplayName("inherit-persistence defaults to off")
    void inheritPersistenceDefaultsOff() {
        assertFalse(config.isInheritPersistence());
    }

    @Test
    @DisplayName("baby-scale is clamped to [0.1, 1.0] and growth-duration to at least 1")
    void growthValuesAreClamped() {
        plugin.getConfig().set("settings.baby-scale", 5.0);
        plugin.getConfig().set("settings.growth-duration-minutes", 0);
        plugin.saveConfig();
        config.loadConfiguration();
        assertEquals(1.0, config.getBabyScale());
        assertEquals(1, config.getGrowthDurationMinutes());

        plugin.getConfig().set("settings.baby-scale", 0.0);
        plugin.saveConfig();
        config.loadConfiguration();
        assertEquals(0.1, config.getBabyScale());
    }

    @Test
    @DisplayName("seed-temptation defaults on with a 10.0-block temptation-radius")
    void temptationDefaults() {
        assertTrue(config.isSeedTemptation());
        assertEquals(10.0, config.getTemptationRadius());
    }

    @Test
    @DisplayName("temptation-radius is clamped to [0.0, 64.0]")
    void temptationRadiusIsClamped() {
        plugin.getConfig().set("settings.temptation-radius", 999.0);
        plugin.saveConfig();
        config.loadConfiguration();
        assertEquals(64.0, config.getTemptationRadius());

        plugin.getConfig().set("settings.temptation-radius", -5.0);
        plugin.saveConfig();
        config.loadConfiguration();
        assertEquals(0.0, config.getTemptationRadius());
    }

    @Test
    @DisplayName("heldBreedingSeed prefers the main-hand seed, falls back to off-hand, else null")
    void heldBreedingSeedPrecedence() {
        ItemStack wheat = new ItemStack(Material.WHEAT_SEEDS);
        ItemStack pumpkin = new ItemStack(Material.PUMPKIN_SEEDS);
        ItemStack stone = new ItemStack(Material.STONE);

        // Main hand wins when both hands hold a breeding seed.
        assertEquals(Material.WHEAT_SEEDS, config.heldBreedingSeed(wheat, pumpkin));
        // Falls back to the off hand when the main hand isn't a seed.
        assertEquals(Material.PUMPKIN_SEEDS, config.heldBreedingSeed(stone, pumpkin));
        assertEquals(Material.PUMPKIN_SEEDS, config.heldBreedingSeed(null, pumpkin));
        // Neither hand holds a breeding seed.
        assertNull(config.heldBreedingSeed(stone, stone));
        assertNull(config.heldBreedingSeed(null, null));
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

    @Test
    @DisplayName("max-tracked-fish is loaded (default 1000)")
    void loadsMaxTrackedFish() {
        assertEquals(1000, config.getMaxTrackedFish());
    }

    @Test
    @DisplayName("debug-logging defaults to off and raises the logger level when enabled")
    void debugLoggingControlsLoggerLevel() {
        assertFalse(config.isDebugLogging());
        assertEquals(java.util.logging.Level.INFO, plugin.getLogger().getLevel());

        plugin.getConfig().set("advanced.debug-logging", true);
        plugin.saveConfig();
        config.loadConfiguration();

        assertTrue(config.isDebugLogging());
        assertEquals(java.util.logging.Level.FINE, plugin.getLogger().getLevel());
    }

    @Test
    @DisplayName("breeding-success-rate defaults to 1.0 and is clamped to [0.0, 1.0]")
    void breedingSuccessRateIsClamped() {
        assertEquals(1.0, config.getBreedingSuccessRate());

        plugin.getConfig().set("settings.breeding-success-rate", 5.0);
        plugin.saveConfig();
        config.loadConfiguration();
        assertEquals(1.0, config.getBreedingSuccessRate());

        plugin.getConfig().set("settings.breeding-success-rate", -2.0);
        plugin.saveConfig();
        config.loadConfiguration();
        assertEquals(0.0, config.getBreedingSuccessRate());
    }

    @Test
    @DisplayName("A missing fish-mappings section is handled gracefully")
    void missingFishMappingsSectionIsSafe() {
        plugin.getConfig().set("fish-mappings", null);
        plugin.saveConfig();

        config.loadConfiguration(); // must not throw

        assertTrue(config.getFishSeedMappings().isEmpty());
    }

    @Test
    @DisplayName("A mapping with a missing seed value is skipped, not fatal")
    void missingSeedValueSkipsOnlyThatEntry() {
        plugin.getConfig().set("fish-mappings.salmon", null); // remove salmon's seed
        plugin.saveConfig();

        config.loadConfiguration(); // must not throw

        // salmon is dropped, but the other mappings still load
        assertNull(config.getSeedForFish(EntityType.SALMON));
        assertEquals(Material.PUMPKIN_SEEDS, config.getSeedForFish(EntityType.COD));
    }

    @Test
    @DisplayName("A mapping with an invalid entity type is skipped, not fatal")
    void invalidEntityTypeSkipsOnlyThatEntry() {
        // "dragon" is not a valid EntityType -> the per-entry valueOf throws and is caught.
        plugin.getConfig().set("fish-mappings.dragon", "wheat_seeds");
        plugin.saveConfig();

        config.loadConfiguration(); // must not throw

        // The bogus entry is dropped; the real defaults still load.
        assertEquals(Material.WHEAT_SEEDS, config.getSeedForFish(EntityType.SALMON));
        assertEquals(Material.PUMPKIN_SEEDS, config.getSeedForFish(EntityType.COD));
    }

    @Test
    @DisplayName("A mapping with an invalid seed material is skipped, not fatal")
    void invalidSeedMaterialSkipsOnlyThatEntry() {
        // A valid fish but a bogus material -> Material.valueOf throws and is caught per entry.
        plugin.getConfig().set("fish-mappings.salmon", "not_a_real_item");
        plugin.saveConfig();

        config.loadConfiguration(); // must not throw

        assertNull(config.getSeedForFish(EntityType.SALMON)); // bad entry dropped
        assertEquals(Material.PUMPKIN_SEEDS, config.getSeedForFish(EntityType.COD)); // others intact
    }

    // ----- section reorganization (1.7.0: growth + success-rate moved to settings) ------

    @Test
    @DisplayName("Options moved to 'settings' are read from their new location")
    void movedOptionsLoadFromSettings() {
        plugin.getConfig().set("settings.natural-growth", false);
        plugin.getConfig().set("settings.baby-scale", 0.3);
        plugin.getConfig().set("settings.growth-duration-minutes", 7);
        plugin.getConfig().set("settings.breeding-success-rate", 0.25);
        plugin.saveConfig();

        config.loadConfiguration();

        assertFalse(config.isNaturalGrowth());
        assertEquals(0.3, config.getBabyScale());
        assertEquals(7, config.getGrowthDurationMinutes());
        assertEquals(0.25, config.getBreedingSuccessRate());
    }

    @Test
    @DisplayName("A moved option left under 'advanced' is ignored (clean cut)")
    void staleAdvancedKeyIsIgnored() {
        // Value only under the OLD location; the new 'settings' key is absent.
        plugin.getConfig().set("advanced.natural-growth", false);
        plugin.saveConfig();

        config.loadConfiguration();

        // The old path is no longer read, so the default (true) is used, not false.
        assertTrue(config.isNaturalGrowth(),
                "a moved key under 'advanced' must be ignored, falling back to the default");
    }

    @Test
    @DisplayName("A stale 'advanced' key triggers a migration warning")
    void staleAdvancedKeyWarns() {
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        plugin.getLogger().addHandler(handler);
        try {
            plugin.getConfig().set("advanced.baby-scale", 0.4);
            plugin.saveConfig();

            config.loadConfiguration();
        } finally {
            plugin.getLogger().removeHandler(handler);
        }

        boolean warned = records.stream().anyMatch(r ->
                r.getLevel() == Level.WARNING
                        && r.getMessage() != null
                        && r.getMessage().contains("baby-scale")
                        && r.getMessage().contains("settings"));
        assertTrue(warned, "expected a WARNING naming the moved key and the 'settings' section");
    }
}
