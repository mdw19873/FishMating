package com.mrsuffix.fishmating.commands;

import com.mrsuffix.fishmating.FishMatingPlugin;
import com.mrsuffix.fishmating.managers.ConfigManager;
import com.mrsuffix.fishmating.models.FishData;
import com.mrsuffix.fishmating.utils.ScaleUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Handles the {@code /fishmating} administrative command (alias {@code /fm}).
 *
 * <p>Sub-commands, all gated by the {@code fishmating.admin} permission:
 * <ul>
 *   <li>{@code reload} — re-read {@code config.yml}.</li>
 *   <li>{@code status} — aggregate summary of the tracked fish.</li>
 *   <li>{@code nearby [radius]} — per-fish detail near the calling player.</li>
 *   <li>{@code config} — dump the live (clamped) configuration values.</li>
 *   <li>{@code grow <radius|all>} — force tracked fish to full size (scale 1.0).</li>
 * </ul>
 *
 * <p>The permission is checked in code (rather than only declared on the command) so an
 * unauthorized sender gets a clear message and the handler can be exercised directly in
 * tests. Every sub-command runs on the main thread, so Bukkit entity access is safe.
 */
public class FishMatingCommand implements TabExecutor {

    /** Permission required for every sub-command. */
    public static final String ADMIN_PERMISSION = "fishmating.admin";

    /** Upper bound (blocks) on the radius accepted by {@code nearby}/{@code grow}. */
    static final double MAX_QUERY_RADIUS = 64.0;

    /** Cap on per-fish lines printed by {@code nearby} to avoid flooding chat. */
    static final int MAX_NEARBY_LINES = 20;

    private static final List<String> SUBCOMMANDS =
            List.of("reload", "status", "nearby", "config", "grow");

    /** Colour for a section heading. */
    private static final NamedTextColor HEADER = NamedTextColor.AQUA;
    /** Colour for a label (the "key" half of a line). */
    private static final NamedTextColor LABEL = NamedTextColor.GRAY;
    /** Colour for a value (the "value" half of a line). */
    private static final NamedTextColor VALUE = NamedTextColor.WHITE;

    private final FishMatingPlugin plugin;

    public FishMatingCommand(FishMatingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(Component.text(
                    "You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "nearby" -> handleNearby(sender, args);
            case "config" -> handleConfig(sender);
            case "grow" -> handleGrow(sender, label, args);
            default -> sendUsage(sender, label);
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text(
                "Usage: /" + label + " <reload|status|nearby|config|grow>", NamedTextColor.YELLOW));
    }

    /** Sends a bold section heading. */
    private void sendHeader(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, HEADER, TextDecoration.BOLD));
    }

    /**
     * Sends an indented "label: value" line with the label and value in distinct colours
     * so the two halves are easy to scan apart.
     */
    private void sendEntry(CommandSender sender, String indent, String label, Object value) {
        sender.sendMessage(Component.text(indent + label + ": ", LABEL)
                .append(Component.text(String.valueOf(value), VALUE)));
    }

    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().loadConfiguration();
        sender.sendMessage(Component.text(
                "FishMating configuration reloaded.", NamedTextColor.GREEN));
    }

    private void handleStatus(CommandSender sender) {
        Map<EntityType, Integer> perType = new EnumMap<>(EntityType.class);
        int total = 0;
        int breedingReady = 0;
        int seeking = 0;
        int growing = 0;

        for (FishData data : plugin.getFishManager().getTrackedFish()) {
            Entity entity = data.getEntity();
            if (entity == null || !entity.isValid()) {
                continue;
            }
            total++;
            perType.merge(entity.getType(), 1, Integer::sum);
            if (data.isBreedingReady()) {
                breedingReady++;
            }
            if (data.getTargetSeed() != null) {
                seeking++;
            }
            if (!ScaleUtil.isFullGrown(entity)) {
                growing++;
            }
        }

        int cap = plugin.getConfigManager().getMaxTrackedFish();
        sendHeader(sender, "FishMating status");
        sendEntry(sender, "  ", "Tracked fish", total + " / " + cap);
        for (Map.Entry<EntityType, Integer> e : perType.entrySet()) {
            sendEntry(sender, "    ", e.getKey().name().toLowerCase(Locale.ROOT), e.getValue());
        }
        sendEntry(sender, "  ", "Breeding-ready", breedingReady);
        sendEntry(sender, "  ", "Seeking a seed", seeking);
        sendEntry(sender, "  ", "Growing (immature)", growing);
        sendEntry(sender, "  ", "Active breeding pairs",
                plugin.getBreedingManager().getActiveBreedingPairCount());
    }

    private void handleNearby(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "Only players can use /fishmating nearby.", NamedTextColor.RED));
            return;
        }

        ConfigManager config = plugin.getConfigManager();
        double radius = config.getDetectionRadius();
        if (args.length >= 2) {
            try {
                radius = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text(
                        "Radius must be a number. Usage: /fishmating nearby [radius]",
                        NamedTextColor.RED));
                return;
            }
        }
        radius = clampRadius(radius);

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        List<Entry> matches = new ArrayList<>();
        for (FishData data : plugin.getFishManager().getTrackedFish()) {
            Entity entity = data.getEntity();
            if (entity == null || !entity.isValid() || !entity.getWorld().equals(player.getWorld())) {
                continue;
            }
            double distSq = distanceSquared(
                    entity.getX(), entity.getY(), entity.getZ(), px, py, pz);
            if (distSq <= radius * radius) {
                matches.add(new Entry(data, distSq));
            }
        }
        matches.sort((a, b) -> Double.compare(a.distSq, b.distSq));

        sender.sendMessage(Component.text("Tracked fish within ", HEADER)
                .append(Component.text(String.format(Locale.ROOT, "%.1f", radius) + " blocks", VALUE))
                .append(Component.text(": ", HEADER))
                .append(Component.text(String.valueOf(matches.size()), VALUE)));

        int shown = Math.min(matches.size(), MAX_NEARBY_LINES);
        for (int i = 0; i < shown; i++) {
            sender.sendMessage(describeFish(matches.get(i), config));
        }
        if (matches.size() > shown) {
            sender.sendMessage(Component.text(
                    "  …and " + (matches.size() - shown) + " more", NamedTextColor.DARK_GRAY));
        }
    }

    /**
     * Builds the one-line component used by {@code nearby}: the fish type stands out in
     * {@link #VALUE}, the distance/maturity sit in {@link #LABEL}, and the breeding state
     * is coloured by kind (ready = green, cooldown = yellow, seeking = aqua, idle = gray).
     */
    private Component describeFish(Entry entry, ConfigManager config) {
        FishData data = entry.data;
        Entity entity = data.getEntity();
        String type = entity.getType().name().toLowerCase(Locale.ROOT);
        String distance = String.format(Locale.ROOT, "%.1f", Math.sqrt(entry.distSq));

        String maturity;
        if (ScaleUtil.isFullGrown(entity)) {
            maturity = "adult";
        } else {
            maturity = "growing " + (int) Math.round(ScaleUtil.getScale(entity) * 100) + "%";
        }

        String state;
        NamedTextColor stateColor;
        long now = System.currentTimeMillis();
        if (data.isBreedingReady()) {
            long left = data.getBreedingReadyTime()
                    + config.getBreedingTimeoutSeconds() * 1000L - now;
            state = "breeding-ready (" + Math.max(0, left / 1000) + "s left)";
            stateColor = NamedTextColor.GREEN;
        } else if (!data.canBreed(config.getBreedingCooldownMinutes())) {
            long left = data.getLastBreedingTime()
                    + config.getBreedingCooldownMinutes() * 60_000L - now;
            state = "cooldown (" + Math.max(0, left / 1000) + "s left)";
            stateColor = NamedTextColor.YELLOW;
        } else if (data.getTargetSeed() != null) {
            state = "seeking";
            stateColor = NamedTextColor.AQUA;
        } else {
            state = "idle";
            stateColor = NamedTextColor.GRAY;
        }

        return Component.text("  • ", NamedTextColor.DARK_GRAY)
                .append(Component.text(type, VALUE))
                .append(Component.text("  " + distance + "b  " + maturity + "  ", LABEL))
                .append(Component.text(state, stateColor));
    }

    private void handleConfig(CommandSender sender) {
        ConfigManager c = plugin.getConfigManager();
        sendHeader(sender, "FishMating effective config");
        sendEntry(sender, "  ", "detection-radius", c.getDetectionRadius());
        sendEntry(sender, "  ", "breeding-timeout-seconds", c.getBreedingTimeoutSeconds());
        sendEntry(sender, "  ", "breeding-cooldown-minutes", c.getBreedingCooldownMinutes());
        sendEntry(sender, "  ", "breeding-experience", c.getBreedingExperience());
        sendEntry(sender, "  ", "enable-particles", c.isParticlesEnabled());
        sendEntry(sender, "  ", "particle-count", c.getParticleCount());
        sendEntry(sender, "  ", "max-tracked-fish", c.getMaxTrackedFish());
        sendEntry(sender, "  ", "natural-growth", c.isNaturalGrowth());
        sendEntry(sender, "  ", "baby-scale", c.getBabyScale());
        sendEntry(sender, "  ", "growth-duration-minutes", c.getGrowthDurationMinutes());
        sendEntry(sender, "  ", "breeding-success-rate", c.getBreedingSuccessRate());
        sendEntry(sender, "  ", "require-player-thrown-seeds", c.isRequirePlayerThrownSeeds());
        sendEntry(sender, "  ", "require-player-within", c.getRequirePlayerWithin());
        sendEntry(sender, "  ", "worldguard-integration", c.isWorldGuardIntegration());
        sendEntry(sender, "  ", "inherit-persistence", c.isInheritPersistence());
        sendEntry(sender, "  ", "debug-logging", c.isDebugLogging());
        sendEntry(sender, "  ", "fish-mappings", describeMappings(c.getFishSeedMappings()));
    }

    private String describeMappings(Map<EntityType, ?> mappings) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<EntityType, ?> e : mappings.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.getKey().name().toLowerCase(Locale.ROOT))
                    .append("=").append(String.valueOf(e.getValue()).toLowerCase(Locale.ROOT));
        }
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

    private void handleGrow(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /" + label + " grow <radius|all>", NamedTextColor.RED));
            return;
        }

        boolean all = args[1].equalsIgnoreCase("all");
        double radius = 0;
        Player player = null;
        if (!all) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text(
                        "Only players can use /fishmating grow <radius>; use 'grow all' from console.",
                        NamedTextColor.RED));
                return;
            }
            player = p;
            try {
                radius = clampRadius(Double.parseDouble(args[1]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text(
                        "Radius must be a number, or use 'all'. Usage: /" + label + " grow <radius|all>",
                        NamedTextColor.RED));
                return;
            }
        }

        double px = player == null ? 0 : player.getX();
        double py = player == null ? 0 : player.getY();
        double pz = player == null ? 0 : player.getZ();

        int grown = 0;
        for (FishData data : plugin.getFishManager().getTrackedFish()) {
            Entity entity = data.getEntity();
            if (entity == null || !entity.isValid() || ScaleUtil.isFullGrown(entity)) {
                continue;
            }
            if (!all) {
                if (!entity.getWorld().equals(player.getWorld())) {
                    continue;
                }
                double distSq = distanceSquared(
                        entity.getX(), entity.getY(), entity.getZ(), px, py, pz);
                if (distSq > radius * radius) {
                    continue;
                }
            }
            // setScale broadcasts a packet to tracking players; we already skipped
            // full-grown fish above so this only fires for fish that actually change.
            ScaleUtil.setScale(entity, 1.0);
            grown++;
        }

        sender.sendMessage(Component.text(
                "Grew " + grown + " fish to full size.", NamedTextColor.GREEN));
    }

    /** Clamps a requested query radius to {@code [0, MAX_QUERY_RADIUS]}. */
    static double clampRadius(double radius) {
        if (radius < 0) {
            return 0;
        }
        return Math.min(radius, MAX_QUERY_RADIUS);
    }

    /** Squared distance between two points (no {@code sqrt}, no {@code Location} alloc). */
    static double distanceSquared(double x1, double y1, double z1,
                                  double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Pairs a tracked fish with its squared distance for sorting. */
    private record Entry(FishData data, double distSq) {
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("grow")) {
            return Stream.of("all")
                    .filter(sub -> sub.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return Collections.emptyList();
    }
}
