package me.nh4n.vcarterutils.command;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MainCommand implements CommandExecutor, TabCompleter {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    public MainCommand(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("vcarterutils.admin")) {
                String noPerm = plugin.getConfig().getString(
                        "score.messages.no-permission",
                        "<glyph:warning_blue> <#f38ba8>You do not have permission."
                );
                sender.sendMessage(miniMessage.deserialize(noPerm));
                return true;
            }

            plugin.reloadPluginConfig();

            String successMsg = plugin.getConfig().getString(
                    "admin.messages.reload-success",
                    "<glyph:warning_blue> <#a6e3a1>VCarterUtils configuration reloaded."
            );
            sender.sendMessage(miniMessage.deserialize(successMsg));
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("setlobby") || args[0].equalsIgnoreCase("setlobbyworld"))) {
            if (!sender.hasPermission("vcarterutils.admin")) {
                String noPerm = plugin.getConfig().getString(
                        "score.messages.no-permission",
                        "<glyph:warning_blue> <#f38ba8>You do not have permission."
                );
                sender.sendMessage(miniMessage.deserialize(noPerm));
                return true;
            }

            String targetWorld;
            if (args.length >= 2) {
                targetWorld = args[1];
            } else if (sender instanceof Player player) {
                targetWorld = player.getWorld().getName();
            } else {
                sender.sendMessage(miniMessage.deserialize("<glyph:warning_blue> <#f38ba8>Usage: /" + label + " setlobby <world>"));
                return true;
            }

            boolean added = plugin.getLobbyHotbarManager().toggleLobbyWorld(targetWorld);
            if (added) {
                String msg = plugin.getConfig().getString(
                        "lobby-hotbar.messages.world-added",
                        "<glyph:warning_blue> <#a6e3a1>Added <#89dceb><b>%world%</b> <#a6e3a1>to Lobby Hotbar worlds."
                );
                sender.sendMessage(miniMessage.deserialize(msg.replace("%world%", targetWorld)));
            } else {
                String msg = plugin.getConfig().getString(
                        "lobby-hotbar.messages.world-removed",
                        "<glyph:warning_blue> <#f38ba8>Removed <#89dceb><b>%world%</b> <#f38ba8>from Lobby Hotbar worlds."
                );
                sender.sendMessage(miniMessage.deserialize(msg.replace("%world%", targetWorld)));
            }
            return true;
        }

        sender.sendMessage(miniMessage.deserialize(
                "<glyph:warning_blue> <#a6adc8>VCarterUtils <#6c7086>| <#89dceb>/" + label + " reload <#6c7086>| <#89dceb>/" + label + " setlobby [world]"
        ));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("vcarterutils.admin")) {
            return completions;
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("reload".startsWith(input)) completions.add("reload");
            if ("setlobby".startsWith(input)) completions.add("setlobby");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("setlobby") || args[0].equalsIgnoreCase("setlobbyworld"))) {
            String input = args[1].toLowerCase();
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().toLowerCase().startsWith(input)) {
                    completions.add(w.getName());
                }
            }
        }
        return completions;
    }
}
