package me.nh4n.vcarterutils.command;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.PearlManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SettingCommand implements CommandExecutor, TabCompleter {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    public SettingCommand(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be executed by players."));
            return true;
        }

        // Ho tro chinh nhanh qua lenh: /setting pearl <seconds>
        if (args.length >= 2 && args[0].equalsIgnoreCase("pearl")) {
            PearlManager pearlManager = plugin.getPearlManager();
            try {
                int seconds = Integer.parseInt(args[1]);
                if (!pearlManager.getAvailableOptions().contains(seconds)) {
                    String invalidMsg = plugin.getConfig().getString(
                            "pearl-cooldown.messages.invalid-option",
                            "<glyph:warning_blue> <#f38ba8>Invalid cooldown option! Available options: <#89dceb>%options%"
                    );
                    String joined = String.join("s, ", pearlManager.getAvailableOptions().stream().map(String::valueOf).toList()) + "s";
                    player.sendMessage(miniMessage.deserialize(invalidMsg.replace("%options%", joined)));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return true;
                }

                pearlManager.setPlayerPersonalSeconds(player.getUniqueId(), seconds);
                String raw = plugin.getConfig().getString(
                        "pearl-cooldown.messages.cooldown-changed",
                        "<glyph:warning_blue> <#a6e3a1>Your personal Ender Pearl cooldown has been set to <#89dceb><b>%seconds%s</b><#a6e3a1>!"
                );
                player.sendMessage(miniMessage.deserialize(raw.replace("%seconds%", String.valueOf(seconds))));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            } catch (NumberFormatException e) {
                String invalidMsg = plugin.getConfig().getString(
                        "pearl-cooldown.messages.invalid-option",
                        "<glyph:warning_blue> <#f38ba8>Invalid cooldown option! Available options: <#89dceb>%options%"
                );
                String joined = String.join("s, ", pearlManager.getAvailableOptions().stream().map(String::valueOf).toList()) + "s";
                player.sendMessage(miniMessage.deserialize(invalidMsg.replace("%options%", joined)));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return true;
        }

        // Mo giao dien /setting
        plugin.getSettingMenuManager().openSettingMenu(player);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if ("pearl".startsWith(args[0].toLowerCase())) {
                completions.add("pearl");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("pearl")) {
            String input = args[1].toLowerCase();
            for (int opt : plugin.getPearlManager().getAvailableOptions()) {
                String s = String.valueOf(opt);
                if (s.startsWith(input)) {
                    completions.add(s);
                }
            }
        }
        return completions;
    }
}
