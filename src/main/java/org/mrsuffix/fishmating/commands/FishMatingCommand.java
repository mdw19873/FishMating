package com.mrsuffix.fishmating.commands;

import com.mrsuffix.fishmating.FishMatingPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Handles the {@code /fishmating} administrative command.
 *
 * <p>The only sub-command is {@code reload}, which re-reads {@code config.yml}. It is
 * gated by the {@code fishmating.admin} permission. The permission is checked in code
 * (rather than only declared on the command) so an unauthorized sender gets a clear
 * message and the handler can be exercised directly in tests.
 */
public class FishMatingCommand implements TabExecutor {

    /** Permission required for every sub-command. */
    public static final String ADMIN_PERMISSION = "fishmating.admin";

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

        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(Component.text("Usage: /" + label + " reload", NamedTextColor.YELLOW));
            return true;
        }

        plugin.getConfigManager().loadConfiguration();
        sender.sendMessage(Component.text(
                "FishMating configuration reloaded.", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission(ADMIN_PERMISSION)) {
            return Stream.of("reload")
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
