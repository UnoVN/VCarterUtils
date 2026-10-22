package me.nh4n.vcarterutils.command;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.MaceManager;
import me.nh4n.vcarterutils.manager.PearlManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
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
            sender.sendMessage(Component.text("Players only."));
            return true;
        }

        // 1. /setting admin
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("vcarterutils.admin")) {
                String noPerm = plugin.getConfig().getString(
                        "score.messages.no-permission",
                        "<glyph:warning_blue> <#f38ba8>You do not have permission."
                );
                player.sendMessage(miniMessage.deserialize(noPerm));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return true;
            }

            plugin.getSettingMenuManager().openAdminSettingMenu(player, 0);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f);
            return true;
        }

        // 2. /setting pearl <seconds>
        if (args.length >= 2 && args[0].equalsIgnoreCase("pearl")) {
            PearlManager pearlManager = plugin.getPearlManager();

            if (pearlManager.isWorldForced(player.getWorld())) {
                showLockedWarning(player, "pearl-cooldown", true, pearlManager.getEffectiveCooldownSeconds(player));
                return true;
            }
            if (pearlManager.isGlobalForceEnabled()) {
                showLockedWarning(player, "pearl-cooldown", false, pearlManager.getEffectiveCooldownSeconds(player));
                return true;
            }

            try {
                int seconds = Integer.parseInt(args[1]);
                if (seconds < pearlManager.getDefaultSeconds() || !pearlManager.getAvailableOptions().contains(seconds)) {
                    String invalidMsg = plugin.getConfig().getString(
                            "pearl-cooldown.messages.invalid-option",
                            "<glyph:warning_blue> <#f38ba8>Invalid cooldown! Available: <#89dceb>%options%"
                    );
                    String joined = String.join("s, ", pearlManager.getAvailableOptions().stream().map(String::valueOf).toList()) + "s";
                    player.sendMessage(miniMessage.deserialize(invalidMsg.replace("%options%", joined)));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return true;
                }

                pearlManager.setPlayerPersonalSeconds(player.getUniqueId(), seconds);
                String raw = plugin.getConfig().getString(
                        "pearl-cooldown.messages.cooldown-changed",
                        "<glyph:warning_blue> <#a6adc8>Pearl cooldown set to <#89dceb><b>%seconds%s</b><#a6adc8>."
                );
                player.sendMessage(miniMessage.deserialize(raw.replace("%seconds%", String.valueOf(seconds))));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            } catch (NumberFormatException e) {
                String invalidMsg = plugin.getConfig().getString(
                        "pearl-cooldown.messages.invalid-option",
                        "<glyph:warning_blue> <#f38ba8>Invalid cooldown! Available: <#89dceb>%options%"
                );
                String joined = String.join("s, ", pearlManager.getAvailableOptions().stream().map(String::valueOf).toList()) + "s";
                player.sendMessage(miniMessage.deserialize(invalidMsg.replace("%options%", joined)));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return true;
        }

        // 3. /setting mace <seconds>
        if (args.length >= 2 && args[0].equalsIgnoreCase("mace")) {
            MaceManager maceManager = plugin.getMaceManager();

            if (maceManager.isWorldForced(player.getWorld())) {
                showLockedWarning(player, "mace-cooldown", true, maceManager.getEffectiveCooldownSeconds(player));
                return true;
            }
            if (maceManager.isGlobalForceEnabled()) {
                showLockedWarning(player, "mace-cooldown", false, maceManager.getEffectiveCooldownSeconds(player));
                return true;
            }

            try {
                int seconds = Integer.parseInt(args[1]);
                if (seconds < maceManager.getDefaultSeconds() || !maceManager.getAvailableOptions().contains(seconds)) {
                    String invalidMsg = plugin.getConfig().getString(
                            "mace-cooldown.messages.invalid-option",
                            "<glyph:warning_blue> <#f38ba8>Invalid cooldown! Available: <#89dceb>%options%"
                    );
                    String joined = String.join("s, ", maceManager.getAvailableOptions().stream().map(String::valueOf).toList()) + "s";
                    player.sendMessage(miniMessage.deserialize(invalidMsg.replace("%options%", joined)));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return true;
                }

                maceManager.setPlayerPersonalSeconds(player.getUniqueId(), seconds);
                String raw = plugin.getConfig().getString(
                        "mace-cooldown.messages.cooldown-changed",
                        "<glyph:warning_blue> <#a6adc8>Mace cooldown set to <#89dceb><b>%seconds%s</b><#a6adc8>."
                );
                player.sendMessage(miniMessage.deserialize(raw.replace("%seconds%", String.valueOf(seconds))));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            } catch (NumberFormatException e) {
                String invalidMsg = plugin.getConfig().getString(
                        "mace-cooldown.messages.invalid-option",
                        "<glyph:warning_blue> <#f38ba8>Invalid cooldown! Available: <#89dceb>%options%"
                );
                String joined = String.join("s, ", maceManager.getAvailableOptions().stream().map(String::valueOf).toList()) + "s";
                player.sendMessage(miniMessage.deserialize(invalidMsg.replace("%options%", joined)));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return true;
        }

        // 4. Open /setting GUI
        plugin.getSettingMenuManager().openSettingMenu(player);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
        return true;
    }

    private void showLockedWarning(Player player, String configPrefix, boolean isWorldForce, int forcedSec) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);

        String titleRaw = plugin.getConfig().getString(
                configPrefix + ".messages.locked-title",
                "<#f38ba8><b>COOLDOWN LOCKED!</b>"
        );
        String subtitleRaw = isWorldForce
                ? plugin.getConfig().getString(
                        configPrefix + ".messages.locked-subtitle-world",
                        "<#f9e2af>Forced in this world: <#89dceb><b>%forced%s</b>"
                  )
                : plugin.getConfig().getString(
                        configPrefix + ".messages.locked-subtitle-global",
                        "<#f9e2af>Forced globally by Admin: <#89dceb><b>%forced%s</b>"
                  );

        Component titleComp = miniMessage.deserialize(titleRaw.replace("%forced%", String.valueOf(forcedSec)));
        Component subtitleComp = miniMessage.deserialize(
                subtitleRaw.replace("%forced%", String.valueOf(forcedSec))
                           .replace("%world%", player.getWorld().getName())
        );

        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(400));
        player.showTitle(Title.title(titleComp, subtitleComp, times));

        String chatRaw = isWorldForce
                ? plugin.getConfig().getString(
                        configPrefix + ".messages.locked-chat-world",
                        "<glyph:warning_blue> <#f38ba8><b>CANNOT CHANGE!</b> <#a6adc8>Cooldown is forced to <#89dceb><b>%forced%s</b> <#a6adc8>in this world."
                  )
                : plugin.getConfig().getString(
                        configPrefix + ".messages.locked-chat-global",
                        "<glyph:warning_blue> <#f38ba8><b>CANNOT CHANGE!</b> <#a6adc8>Cooldown is forced globally to <#89dceb><b>%forced%s</b><#a6adc8>."
                  );

        player.sendMessage(miniMessage.deserialize(
                chatRaw.replace("%forced%", String.valueOf(forcedSec))
                       .replace("%world%", player.getWorld().getName())
        ));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("pearl".startsWith(input)) {
                completions.add("pearl");
            }
            if ("mace".startsWith(input)) {
                completions.add("mace");
            }
            if (sender.hasPermission("vcarterutils.admin") && "admin".startsWith(input)) {
                completions.add("admin");
            }
        } else if (args.length == 2) {
            String input = args[1].toLowerCase();
            if (args[0].equalsIgnoreCase("pearl")) {
                for (int opt : plugin.getPearlManager().getAvailableOptions()) {
                    String s = String.valueOf(opt);
                    if (s.startsWith(input)) {
                        completions.add(s);
                    }
                }
            } else if (args[0].equalsIgnoreCase("mace")) {
                for (int opt : plugin.getMaceManager().getAvailableOptions()) {
                    String s = String.valueOf(opt);
                    if (s.startsWith(input)) {
                        completions.add(s);
                    }
                }
            }
        }
        return completions;
    }
}
