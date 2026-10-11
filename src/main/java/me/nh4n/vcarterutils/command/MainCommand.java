package me.nh4n.vcarterutils.command;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
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

    private static final TextColor COLOR_SKY     = TextColor.fromHexString("#89dceb");
    private static final TextColor COLOR_RED     = TextColor.fromHexString("#f38ba8");
    private static final TextColor COLOR_SUBTEXT = TextColor.fromHexString("#a6adc8");

    public MainCommand(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("vcarterutils.admin")) {
                sender.sendMessage(miniMessage.deserialize("<glyph:warning_blue> ").append(Component.text("You do not have permission to execute this command.", COLOR_RED)));
                return true;
            }

            plugin.reloadPluginConfig();

            String successMsg = plugin.getConfig().getString(
                    "admin.messages.reload-success",
                    "<glyph:warning_blue> <#a6e3a1>VCarterUtils configuration and regear whitelist reloaded successfully!"
            );
            sender.sendMessage(miniMessage.deserialize(successMsg));
            return true;
        }

        // Hien thi huong dan lenh
        Component usage = miniMessage.deserialize("<glyph:warning_blue> ")
                .append(Component.text("VCarterUtils v1.0.0 | Usage: ", COLOR_SUBTEXT))
                .append(Component.text("/" + label + " reload", COLOR_SKY));
        sender.sendMessage(usage);
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
