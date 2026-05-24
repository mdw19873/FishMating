package com.mrsuffix.fishmating.commands;

import com.mrsuffix.fishmating.FishMatingPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.command.MessageTarget;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@code /fishmating} and its sub-commands ({@code reload}, {@code status},
 * {@code nearby}, {@code config}, {@code grow}), including the {@code fishmating.admin}
 * permission gate.
 *
 * <p>Reload success is observed by staging a changed mapping on disk. Query output is
 * captured via the mock sender's component-message queue and serialized to plain text.
 *
 * <p>Note: MockBukkit does not implement the scale attribute, so {@code ScaleUtil}
 * reports every fish as full-grown and {@code setScale} is a no-op. The {@code grow}
 * tests therefore exercise dispatch / permission / validation and the selection path,
 * not the actual scale write (which is covered by the floor/ceiling compile guards).
 */
class FishMatingCommandTest {

    private ServerMock server;
    private FishMatingPlugin plugin;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(FishMatingPlugin.class);
        world = server.addSimpleWorld("w");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Drains all queued component messages from a mock sender into one plain-text blob. */
    private String drain(MessageTarget target) {
        StringBuilder sb = new StringBuilder();
        Component next;
        while ((next = target.nextComponentMessage()) != null) {
            sb.append(PlainTextComponentSerializer.plainText().serialize(next)).append('\n');
        }
        return sb.toString();
    }

    /** Spawns a fish of the given type and registers it with the manager (tracked). */
    private Entity trackFish(EntityType type, double x, double y, double z) {
        Entity fish = world.spawnEntity(new Location(world, x, y, z), type);
        plugin.getFishManager().getFishData(fish); // ensure tracked (idempotent)
        return fish;
    }

    private PlayerMock opPlayer() {
        PlayerMock player = server.addPlayer();
        player.setOp(true); // fishmating.admin defaults to op
        return player;
    }

    /** Rewrites salmon's seed on disk; the live config keeps the old value until reload. */
    private void stageDiskChange() {
        plugin.getConfig().set("fish-mappings.salmon", "melon_seeds");
        plugin.saveConfig();
    }

    // ----- reload -----------------------------------------------------------------

    @Test
    @DisplayName("An authorized sender reloads the config and the change is applied")
    void authorizedReloadAppliesChange() {
        assertEquals(Material.WHEAT_SEEDS, plugin.getConfigManager().getSeedForFish(EntityType.SALMON));
        stageDiskChange();

        server.execute("fishmating", opPlayer(), "reload");

        assertEquals(Material.MELON_SEEDS, plugin.getConfigManager().getSeedForFish(EntityType.SALMON));
    }

    @Test
    @DisplayName("An unauthorized sender cannot trigger a reload")
    void unauthorizedReloadIsBlocked() {
        stageDiskChange();

        PlayerMock player = server.addPlayer(); // not op -> lacks fishmating.admin
        server.execute("fishmating", player, "reload");

        assertEquals(Material.WHEAT_SEEDS, plugin.getConfigManager().getSeedForFish(EntityType.SALMON));
        assertTrue(drain(player).contains("do not have permission"));
    }

    @Test
    @DisplayName("An unknown sub-command shows usage")
    void unknownSubcommandShowsUsage() {
        PlayerMock player = opPlayer();
        server.execute("fishmating", player, "bogus");
        assertTrue(drain(player).contains("Usage:"));
    }

    // ----- status -----------------------------------------------------------------

    @Test
    @DisplayName("status reports total, per-type, and breeding-ready counts")
    void statusReportsCounts() {
        trackFish(EntityType.SALMON, 0, 64, 0);
        Entity salmon2 = trackFish(EntityType.SALMON, 1, 64, 0);
        trackFish(EntityType.COD, 2, 64, 0);
        plugin.getFishManager().getFishData(salmon2).setBreedingReady(true);

        PlayerMock player = opPlayer();
        server.execute("fishmating", player, "status");
        String out = drain(player);

        assertTrue(out.contains("Tracked fish: 3 / 1000"), out);
        assertTrue(out.contains("salmon: 2"), out);
        assertTrue(out.contains("cod: 1"), out);
        assertTrue(out.contains("Breeding-ready: 1"), out);
        assertTrue(out.contains("Active breeding pairs: 0"), out);
    }

    @Test
    @DisplayName("status with no tracked fish reports zero against the cap")
    void statusWithNoFish() {
        PlayerMock player = opPlayer();
        server.execute("fishmating", player, "status");
        assertTrue(drain(player).contains("Tracked fish: 0 / 1000"));
    }

    // ----- nearby -----------------------------------------------------------------

    @Test
    @DisplayName("nearby lists fish within the radius and excludes those outside it")
    void nearbyFiltersByRadius() {
        PlayerMock player = opPlayer();
        player.teleport(new Location(world, 0, 64, 0));
        trackFish(EntityType.SALMON, 2, 64, 0);   // within default radius 5.0
        trackFish(EntityType.COD, 50, 64, 0);     // outside

        server.execute("fishmating", player, "nearby");
        String out = drain(player);

        assertTrue(out.contains("within 5.0 blocks: 1"), out);
        assertTrue(out.contains("salmon"), out);
        assertFalse(out.contains("cod"), out);
    }

    @Test
    @DisplayName("nearby from the console is rejected (players only)")
    void nearbyConsoleRejected() {
        ConsoleCommandSenderMock console = server.getConsoleSender();
        server.execute("fishmating", console, "nearby");
        assertTrue(drain(console).contains("Only players"));
    }

    @Test
    @DisplayName("nearby with a non-numeric radius shows an error")
    void nearbyInvalidRadius() {
        PlayerMock player = opPlayer();
        server.execute("fishmating", player, "nearby", "abc");
        assertTrue(drain(player).contains("must be a number"));
    }

    // ----- config -----------------------------------------------------------------

    @Test
    @DisplayName("config dumps the live (clamped) values")
    void configDumpsLiveValues() {
        PlayerMock player = opPlayer();
        server.execute("fishmating", player, "config");
        String out = drain(player);

        assertTrue(out.contains("detection-radius: 5.0"), out);
        assertTrue(out.contains("max-tracked-fish: 1000"), out);
        assertTrue(out.contains("breeding-cooldown-minutes: 5"), out);
    }

    // ----- grow -------------------------------------------------------------------

    @Test
    @DisplayName("grow with no argument shows usage")
    void growMissingArgShowsUsage() {
        PlayerMock player = opPlayer();
        server.execute("fishmating", player, "grow");
        assertTrue(drain(player).contains("grow <radius|all>"));
    }

    @Test
    @DisplayName("grow <radius> from the console is rejected (players only)")
    void growRadiusConsoleRejected() {
        ConsoleCommandSenderMock console = server.getConsoleSender();
        server.execute("fishmating", console, "grow", "10");
        assertTrue(drain(console).contains("Only players"));
    }

    @Test
    @DisplayName("grow with a non-numeric radius shows an error")
    void growNonNumericRadius() {
        PlayerMock player = opPlayer();
        server.execute("fishmating", player, "grow", "xyz");
        assertTrue(drain(player).contains("must be a number"));
    }

    @Test
    @DisplayName("grow all dispatches and reports a count")
    void growAllReportsCount() {
        // All mocked fish read as full-grown, so none change; the command still succeeds.
        trackFish(EntityType.SALMON, 0, 64, 0);
        PlayerMock player = opPlayer();
        server.execute("fishmating", player, "grow", "all");
        assertTrue(drain(player).contains("Grew 0 fish to full size."));
    }

    @Test
    @DisplayName("grow <radius> dispatches for a player")
    void growRadiusReportsCount() {
        PlayerMock player = opPlayer();
        player.teleport(new Location(world, 0, 64, 0));
        trackFish(EntityType.SALMON, 1, 64, 0);
        server.execute("fishmating", player, "grow", "10");
        assertTrue(drain(player).contains("Grew "));
    }

    // ----- permission gate & tab completion ---------------------------------------

    @Test
    @DisplayName("An unauthorized sender is blocked from a query sub-command")
    void unauthorizedQueryBlocked() {
        PlayerMock player = server.addPlayer(); // not op
        server.execute("fishmating", player, "status");
        assertTrue(drain(player).contains("do not have permission"));
    }

    @Test
    @DisplayName("Tab completion lists all sub-commands to an authorized sender")
    void tabCompletionListsSubcommands() {
        FishMatingCommand handler = new FishMatingCommand(plugin);
        List<String> all = handler.onTabComplete(opPlayer(), null, "fishmating", new String[]{""});
        assertEquals(List.of("reload", "status", "nearby", "config", "grow"), all);
    }

    @Test
    @DisplayName("Tab completion filters sub-commands by prefix")
    void tabCompletionFiltersByPrefix() {
        FishMatingCommand handler = new FishMatingCommand(plugin);
        List<String> filtered = handler.onTabComplete(opPlayer(), null, "fishmating", new String[]{"st"});
        assertEquals(List.of("status"), filtered);
    }

    @Test
    @DisplayName("Tab completion suggests 'all' for grow's second argument")
    void tabCompletionGrowSecondArg() {
        FishMatingCommand handler = new FishMatingCommand(plugin);
        List<String> suggestions = handler.onTabComplete(
                opPlayer(), null, "fishmating", new String[]{"grow", ""});
        assertEquals(List.of("all"), suggestions);
    }

    @Test
    @DisplayName("Tab completion offers nothing to an unauthorized sender")
    void tabCompletionUnauthorized() {
        FishMatingCommand handler = new FishMatingCommand(plugin);
        PlayerMock player = server.addPlayer(); // not op
        assertTrue(handler.onTabComplete(player, null, "fishmating", new String[]{""}).isEmpty());
    }

    // ----- pure helpers -----------------------------------------------------------

    @Test
    @DisplayName("clampRadius bounds the radius to [0, MAX_QUERY_RADIUS]")
    void clampRadiusBounds() {
        assertEquals(0.0, FishMatingCommand.clampRadius(-5));
        assertEquals(10.0, FishMatingCommand.clampRadius(10));
        assertEquals(FishMatingCommand.MAX_QUERY_RADIUS, FishMatingCommand.clampRadius(1000));
    }

    @Test
    @DisplayName("distanceSquared computes squared euclidean distance")
    void distanceSquaredComputes() {
        assertEquals(25.0, FishMatingCommand.distanceSquared(0, 0, 0, 3, 4, 0));
        assertEquals(0.0, FishMatingCommand.distanceSquared(1, 2, 3, 1, 2, 3));
    }
}
