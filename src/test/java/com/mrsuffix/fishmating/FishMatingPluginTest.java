package com.mrsuffix.fishmating;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests that boot the real plugin on an in-memory MockBukkit server.
 *
 * <p>This exercises {@code onEnable()} end to end: manager construction, scheduler
 * task registration, and event-listener wiring — without a live Minecraft server.
 */
class FishMatingPluginTest {

    private ServerMock server;
    private FishMatingPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(FishMatingPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("The plugin enables cleanly and wires up all three managers")
    void pluginEnablesWithManagers() {
        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getConfigManager());
        assertNotNull(plugin.getFishManager());
        assertNotNull(plugin.getBreedingManager());
    }

    @Test
    @DisplayName("getInstance() returns the loaded plugin singleton")
    void exposesSingletonInstance() {
        assertSame(plugin, FishMatingPlugin.getInstance());
    }

    @Test
    @DisplayName("Two scheduler tasks (fish update + breeding check) are registered")
    void schedulesPeriodicTasks() {
        // FishManager and BreedingManager each register one repeating task in their
        // constructors; assert the server scheduler is tracking them.
        assertTrue(server.getScheduler().getPendingTasks().size() >= 2);
    }
}
