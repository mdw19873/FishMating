package com.mrsuffix.fishmating.listeners;

import com.mrsuffix.fishmating.FishMatingPlugin;
import com.mrsuffix.fishmating.managers.FishManager;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Integration test for {@link EntityListener}: when a tracked fish dies, its
 * {@link com.mrsuffix.fishmating.models.FishData} must be evicted so the manager's
 * tracking map does not leak.
 */
class EntityListenerTest {

    private ServerMock server;
    private FishMatingPlugin plugin;
    private FishManager fishManager;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(FishMatingPlugin.class);
        fishManager = plugin.getFishManager();
        world = server.addSimpleWorld("w");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Death of a tracked fish removes its FishData")
    void deathRemovesFishData() {
        Entity salmon = world.spawnEntity(new Location(world, 0, 64, 0), EntityType.SALMON);
        fishManager.getFishData(salmon).setBreedingReady(true);

        DamageSource source = DamageSource.builder(DamageType.GENERIC).build();
        EntityDeathEvent event = new EntityDeathEvent((LivingEntity) salmon, source, List.of());
        server.getPluginManager().callEvent(event);

        // The old (breeding-ready) entry is gone; a fresh, not-ready instance is returned.
        assertFalse(fishManager.getFishData(salmon).isBreedingReady());
    }
}
