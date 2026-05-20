package com.mrsuffix.fishmating.commands;

import com.mrsuffix.fishmating.FishMatingPlugin;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@code /fishmating reload}, including its {@code fishmating.admin}
 * permission gate.
 *
 * <p>Reload success is observed by staging a changed mapping on disk and asserting the
 * live {@link com.mrsuffix.fishmating.managers.ConfigManager} only reflects it after a
 * successful reload.
 */
class FishMatingCommandTest {

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

    /** Rewrites salmon's seed on disk; the live config keeps the old value until reload. */
    private void stageDiskChange() {
        plugin.getConfig().set("fish-mappings.salmon", "melon_seeds");
        plugin.saveConfig();
    }

    private EntityType salmon() {
        return EntityType.SALMON;
    }

    @Test
    @DisplayName("An authorized sender reloads the config and the change is applied")
    void authorizedReloadAppliesChange() {
        assertEquals(Material.WHEAT_SEEDS, plugin.getConfigManager().getSeedForFish(salmon()));
        stageDiskChange();

        PlayerMock player = server.addPlayer();
        player.setOp(true); // fishmating.admin defaults to op

        server.execute("fishmating", player, "reload");

        assertEquals(Material.MELON_SEEDS, plugin.getConfigManager().getSeedForFish(salmon()));
    }

    @Test
    @DisplayName("An unauthorized sender cannot trigger a reload")
    void unauthorizedReloadIsBlocked() {
        stageDiskChange();

        PlayerMock player = server.addPlayer(); // not op -> lacks fishmating.admin
        server.execute("fishmating", player, "reload");

        // The reload did not run: the live config still has the original mapping.
        assertEquals(Material.WHEAT_SEEDS, plugin.getConfigManager().getSeedForFish(salmon()));
    }

    @Test
    @DisplayName("An unknown sub-command shows usage and does not reload")
    void unknownSubcommandDoesNotReload() {
        stageDiskChange();

        PlayerMock player = server.addPlayer();
        player.setOp(true);
        server.execute("fishmating", player, "bogus");

        assertEquals(Material.WHEAT_SEEDS, plugin.getConfigManager().getSeedForFish(salmon()));
    }

    @Test
    @DisplayName("Tab completion suggests 'reload' to an authorized sender")
    void tabCompletionSuggestsReload() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        FishMatingCommand handler = new FishMatingCommand(plugin);
        List<String> suggestions = handler.onTabComplete(player, null, "fishmating", new String[]{""});

        assertEquals(List.of("reload"), suggestions);
    }
}
