package me.nh4n.vcarterutils.command;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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
                        "<glyph:warning_blue> <#f38ba8>No permission."
                );
                sender.sendMessage(miniMessage.deserialize(noPerm));
                return true;
            }

            plugin.reloadPluginConfig();

            String successMsg = plugin.getConfig().getString(
                    "admin.messages.reload-success",
                    "<glyph:warning_blue> <white><sprite:items:item/comparator></white> <#a6e3a1>VCarterUtils reloaded!"
            );
            sender.sendMessage(miniMessage.deserialize(successMsg));
            return true;
        }

        sender.sendMessage(miniMessage.deserialize(
                "<glyph:warning_blue> <white><sprite:items:item/comparator></white> <#a6adc8>VCarterUtils <#6c7086>| <#89dceb>/" + label + " reload"
        ));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("vcarterutils.admin") && "reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
        }
        return completions;
    }
}
