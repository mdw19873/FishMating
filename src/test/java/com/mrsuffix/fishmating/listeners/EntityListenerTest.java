package com.mrsuffix.fishmating.listeners;

import com.mrsuffix.fishmating.FishMatingPlugin;
import com.mrsuffix.fishmating.managers.FishManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.SimpleEntityMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    @DisplayName("A full-grown fish keeps its drops and XP on death")
    void adultFishDropsPreserved() {
        // A wild/grown fish (scale 1.0 — MockBukkit reports no scale attribute, treated as
        // adult) must drop normally; only not-yet-grown fish are suppressed. Guards against
        // the suppression handler over-reaching and clearing legitimate adult loot.
        Entity salmon = world.spawnEntity(new Location(world, 0, 64, 0), EntityType.SALMON);

        DamageSource source = DamageSource.builder(DamageType.GENERIC).build();
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.SALMON)));
        EntityDeathEvent event = new EntityDeathEvent((LivingEntity) salmon, source, drops, 3);
        server.getPluginManager().callEvent(event);

        assertEquals(1, event.getDrops().size(), "adult fish loot should be untouched");
        assertEquals(3, event.getDroppedExp(), "adult fish XP should be untouched");
    }

    @Test
    @DisplayName("A spawning fish of a mapped type is tracked")
    void spawnTracksMappedFish() {
        Entity salmon = world.spawnEntity(new Location(world, 0, 64, 0), EntityType.SALMON);
        fishManager.removeFishData(salmon); // baseline: untracked

        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntitySpawnEvent(salmon));

        assertEquals(1, fishManager.getTrackedFish().size());
    }

    @Test
    @DisplayName("A chunk's entities loading tracks the mapped fish among them")
    void entitiesLoadTracksMappedFish() {
        Entity salmon = world.spawnEntity(new Location(world, 0, 64, 0), EntityType.SALMON);
        fishManager.removeFishData(salmon); // baseline: untracked
        Chunk chunk = world.getChunkAt(salmon.getLocation());

        server.getPluginManager().callEvent(new EntitiesLoadEvent(chunk, List.of(salmon)));

        assertEquals(1, fishManager.getTrackedFish().size());
    }

    @Test
    @DisplayName("Entities-load ignores entities that are not mapped fish")
    void entitiesLoadIgnoresNonFish() {
        Chunk chunk = world.getChunkAt(new Location(world, 0, 64, 0));
        Entity notAFish = new SimpleEntityMock(server); // type UNKNOWN

        server.getPluginManager().callEvent(new EntitiesLoadEvent(chunk, List.of(notAFish)));

        assertTrue(fishManager.getTrackedFish().isEmpty());
    }

    /** Builds a bucket-capture event for the given entity (water bucket -> fish bucket). */
    private PlayerBucketEntityEvent bucketEvent(Entity entity) {
        PlayerMock player = server.addPlayer();
        return new PlayerBucketEntityEvent(player, entity,
                new ItemStack(Material.WATER_BUCKET), new ItemStack(Material.SALMON_BUCKET),
                EquipmentSlot.HAND);
    }

    @Test
    @DisplayName("Bucketing a full-grown mapped fish is allowed")
    void bucketingAdultFishAllowed() {
        // MockBukkit reports no scale attribute, so the fish reads as full-grown; the
        // immature-cancel branch relies on ScaleUtil.isFullGrown (unit-tested) + the
        // floor/ceiling compile guards, as with suppressImmatureFishDrops.
        Entity salmon = world.spawnEntity(new Location(world, 0, 64, 0), EntityType.SALMON);

        PlayerBucketEntityEvent event = bucketEvent(salmon);
        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled(), "an adult fish should be bucketable");
    }

    @Test
    @DisplayName("Bucketing a non-mapped entity is ignored")
    void bucketingNonFishIgnored() {
        Entity notAFish = new SimpleEntityMock(server); // type UNKNOWN

        PlayerBucketEntityEvent event = bucketEvent(notAFish);
        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled(), "non-mapped entities are not the plugin's concern");
    }

    @Test
    @DisplayName("Spawn of a non-mapped entity is not tracked")
    void spawnIgnoresNonFish() {
        Entity notAFish = new SimpleEntityMock(server); // type UNKNOWN

        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntitySpawnEvent(notAFish));

        assertTrue(fishManager.getTrackedFish().isEmpty());
    }

    @Test
    @DisplayName("Death of a non-mapped entity is left untouched (drops/XP preserved)")
    void deathOfNonFishIsIgnored() {
        // A chicken is a LivingEntity but not a mapped fish, so both death handlers
        // (drop suppression and untracking) must short-circuit on the type guard.
        Entity chicken = world.spawnEntity(new Location(world, 0, 64, 0), EntityType.CHICKEN);

        DamageSource source = DamageSource.builder(DamageType.GENERIC).build();
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.FEATHER)));
        EntityDeathEvent event = new EntityDeathEvent((LivingEntity) chicken, source, drops, 5);
        server.getPluginManager().callEvent(event);

        assertEquals(1, event.getDrops().size(), "non-mapped loot must be untouched");
        assertEquals(5, event.getDroppedExp(), "non-mapped XP must be untouched");
        assertTrue(fishManager.getTrackedFish().isEmpty());
    }
}
