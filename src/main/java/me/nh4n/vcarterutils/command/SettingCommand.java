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

        // 1. /setting admin [subcommands]
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("vcarterutils.admin")) {
                String noPerm = plugin.getConfig().getString(
                        "score.messages.no-permission",
                        "%prefix_error%%c_error%<b>ɴᴏ ᴘᴇʀᴍɪssɪᴏɴ</b> %c_muted%» %c_text%You do not have permission."
                );
                player.sendMessage(plugin.parseMessage(noPerm));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return true;
            }

            // /setting admin global
            if (args.length >= 2 && args[1].equalsIgnoreCase("global")) {
                plugin.getSettingMenuManager().openGlobalDetailMenu(player);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
                return true;
            }

            // /setting admin world <world>
            if (args.length >= 3 && args[1].equalsIgnoreCase("world")) {
                String targetWorld = args[2];
                plugin.getSettingMenuManager().openWorldDetailMenu(player, targetWorld);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
                return true;
            }

            // /setting admin combat <world> <seconds|off|disable>
            if (args.length >= 4 && args[1].equalsIgnoreCase("combat")) {
                String targetWorld = args[2];
                String valStr = args[3].toLowerCase();
                var combatManager = plugin.getCombatTagManager();
                if (valStr.equals("off")) {
                    combatManager.setWorldForceSeconds(targetWorld, -1);
                    player.sendMessage(plugin.parseMessage("%prefix_admin%%c_primary%<b>" + targetWorld + "</b> %c_muted%» %c_text%Combat Tag: <#f38ba8><b>✖ ᴏꜰꜰ (ᴅᴇꜰᴀᴜʟᴛ)</b>"));
                } else if (valStr.equals("disable") || valStr.equals("disabled") || valStr.equals("0")) {
                    combatManager.setWorldForceSeconds(targetWorld, 0);
                    player.sendMessage(plugin.parseMessage("%prefix_admin%%c_primary%<b>" + targetWorld + "</b> %c_muted%» %c_text%Combat Tag: <#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ (0s)</b>"));
                } else {
                    try {
                        int sec = Integer.parseInt(valStr);
                        combatManager.setWorldForceSeconds(targetWorld, sec);
                        player.sendMessage(plugin.parseMessage("%prefix_admin%%c_primary%<b>" + targetWorld + "</b> %c_muted%» %c_text%Combat Tag set to %c_primary%<b>" + sec + "s</b>%c_text%."));
                    } catch (NumberFormatException e) {
                        player.sendMessage(plugin.parseMessage("%prefix_error%%c_error%<b>ɪɴᴠᴀʟɪᴅ</b> %c_muted%» %c_text%Use a number, %c_primary%disable%c_text%, or %c_primary%off%c_text%."));
                        return true;
                    }
                }
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                return true;
            }

            // /setting admin pearl <world> <seconds|off>
            if (args.length >= 4 && args[1].equalsIgnoreCase("pearl")) {
                String targetWorld = args[2];
                String valStr = args[3].toLowerCase();
                var pearlManager = plugin.getPearlManager();
                if (valStr.equals("off")) {
                    pearlManager.setWorldForceSeconds(targetWorld, -1);
                    player.sendMessage(plugin.parseMessage("%prefix_admin%%c_primary%<b>" + targetWorld + "</b> %c_muted%» %c_text%Pearl Force: <#f38ba8><b>✖ ᴏꜰꜰ</b>"));
                } else {
                    try {
                        int sec = Integer.parseInt(valStr);
                        pearlManager.setWorldForceSeconds(targetWorld, sec);
                        player.sendMessage(plugin.parseMessage("%prefix_admin%%c_primary%<b>" + targetWorld + "</b> %c_muted%» %c_text%Pearl cooldown forced to %c_primary%<b>" + sec + "s</b>%c_text%."));
                    } catch (NumberFormatException e) {
                        player.sendMessage(plugin.parseMessage("%prefix_error%%c_error%<b>ɪɴᴠᴀʟɪᴅ</b> %c_muted%» %c_text%Use a number or %c_primary%off%c_text%."));
                        return true;
                    }
                }
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                return true;
            }

            // /setting admin mace <world> <seconds|off>
            if (args.length >= 4 && args[1].equalsIgnoreCase("mace")) {
                String targetWorld = args[2];
                String valStr = args[3].toLowerCase();
                var maceManager = plugin.getMaceManager();
                if (valStr.equals("off")) {
                    maceManager.setWorldForceSeconds(targetWorld, -1);
                    player.sendMessage(plugin.parseMessage("%prefix_admin%%c_primary%<b>" + targetWorld + "</b> %c_muted%» %c_text%Mace Force: <#f38ba8><b>✖ ᴏꜰꜰ</b>"));
                } else {
                    try {
                        int sec = Integer.parseInt(valStr);
                        maceManager.setWorldForceSeconds(targetWorld, sec);
                        player.sendMessage(plugin.parseMessage("%prefix_admin%%c_primary%<b>" + targetWorld + "</b> %c_muted%» %c_text%Mace cooldown forced to %c_primary%<b>" + sec + "s</b>%c_text%."));
                    } catch (NumberFormatException e) {
                        player.sendMessage(plugin.parseMessage("%prefix_error%%c_error%<b>ɪɴᴠᴀʟɪᴅ</b> %c_muted%» %c_text%Use a number or %c_primary%off%c_text%."));
                        return true;
                    }
                }
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                return true;
            }

            // /setting admin interrupt <world> <on|off|default>
            if (args.length >= 4 && args[1].equalsIgnoreCase("interrupt")) {
                String targetWorld = args[2];
                String valStr = args[3].toLowerCase();
                var combatManager = plugin.getCombatTagManager();
                if (valStr.equals("default") || valStr.equals("reset")) {
                    combatManager.setWorldAntiInterrupt(targetWorld, null);
                    player.sendMessage(plugin.parseMessage("%prefix_admin%%c_primary%<b>" + targetWorld + "</b> %c_muted%» %c_text%Anti-Interrupt: <#6c7086><b>ᴅᴇꜰᴀᴜʟᴛ</b>"));
                } else if (valStr.equals("on") || valStr.equals("enable") || valStr.equals("true")) {
                    combatManager.setWorldAntiInterrupt(targetWorld, true);
                    player.sendMessage(plugin.parseMessage("%prefix_admin%%c_primary%<b>" + targetWorld + "</b> %c_muted%» %c_text%Anti-Interrupt: <#a6e3a1><b>✔ ᴇɴᴀʙʟᴇᴅ</b>"));
                } else if (valStr.equals("off") || valStr.equals("disable") || valStr.equals("false")) {
                    combatManager.setWorldAntiInterrupt(targetWorld, false);
                    player.sendMessage(plugin.parseMessage("%prefix_admin%%c_primary%<b>" + targetWorld + "</b> %c_muted%» %c_text%Anti-Interrupt: <#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ</b>"));
                } else {
                    player.sendMessage(plugin.parseMessage("%prefix_error%%c_error%<b>ɪɴᴠᴀʟɪᴅ</b> %c_muted%» %c_text%Use %c_primary%on%c_text%, %c_primary%off%c_text%, or %c_primary%default%c_text%."));
                    return true;
                }
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
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
                            "%prefix_error%%c_error%Invalid option! %c_text%Available %c_muted%» %c_primary%%options%"
                    );
                    String joined = String.join("s, ", pearlManager.getAvailableOptions().stream().map(String::valueOf).toList()) + "s";
                    player.sendMessage(plugin.parseMessage(invalidMsg.replace("%options%", joined)));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return true;
                }

                pearlManager.setPlayerPersonalSeconds(player.getUniqueId(), seconds);
                String raw = plugin.getConfig().getString(
                        "pearl-cooldown.messages.cooldown-changed",
                        "<white><sprite:items:item/ender_pearl></white> %c_text%Pearl cooldown set to %c_primary%<b>%seconds%s</b>%c_text%."
                );
                player.sendMessage(plugin.parseMessage(raw.replace("%seconds%", String.valueOf(seconds))));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            } catch (NumberFormatException e) {
                String invalidMsg = plugin.getConfig().getString(
                        "pearl-cooldown.messages.invalid-option",
                        "%prefix_error%%c_error%Invalid option! %c_text%Available %c_muted%» %c_primary%%options%"
                    );
                String joined = String.join("s, ", pearlManager.getAvailableOptions().stream().map(String::valueOf).toList()) + "s";
                player.sendMessage(plugin.parseMessage(invalidMsg.replace("%options%", joined)));
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
                            "%prefix_error%%c_error%Invalid option! %c_text%Available %c_muted%» %c_primary%%options%"
                    );
                    String joined = String.join("s, ", maceManager.getAvailableOptions().stream().map(String::valueOf).toList()) + "s";
                    player.sendMessage(plugin.parseMessage(invalidMsg.replace("%options%", joined)));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return true;
                }

                maceManager.setPlayerPersonalSeconds(player.getUniqueId(), seconds);
                String raw = plugin.getConfig().getString(
                        "mace-cooldown.messages.cooldown-changed",
                        "<white><sprite:items:item/mace></white> %c_text%Mace cooldown set to %c_primary%<b>%seconds%s</b>%c_text%."
                );
                player.sendMessage(plugin.parseMessage(raw.replace("%seconds%", String.valueOf(seconds))));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            } catch (NumberFormatException e) {
                String invalidMsg = plugin.getConfig().getString(
                        "mace-cooldown.messages.invalid-option",
                        "%prefix_error%%c_error%Invalid option! %c_text%Available %c_muted%» %c_primary%%options%"
                    );
                String joined = String.join("s, ", maceManager.getAvailableOptions().stream().map(String::valueOf).toList()) + "s";
                player.sendMessage(plugin.parseMessage(invalidMsg.replace("%options%", joined)));
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
                "%c_error%<b>ᴄᴏᴏʟᴅᴏᴡɴ ʟᴏᴄᴋᴇᴅ</b>"
        );
        String subtitleRaw = isWorldForce
                ? plugin.getConfig().getString(
                        configPrefix + ".messages.locked-subtitle-world",
                        "%c_text%Forced in %c_primary%%world% %c_muted%» %c_warning%<b>%forced%s</b>"
                  )
                : plugin.getConfig().getString(
                        configPrefix + ".messages.locked-subtitle-global",
                        "%c_text%Forced Globally %c_muted%» %c_warning%<b>%forced%s</b>"
                  );

        Component titleComp = plugin.parseMessage(titleRaw.replace("%forced%", String.valueOf(forcedSec)));
        Component subtitleComp = plugin.parseMessage(
                subtitleRaw.replace("%forced%", String.valueOf(forcedSec))
                           .replace("%world%", player.getWorld().getName())
        );

        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(400));
        player.showTitle(Title.title(titleComp, subtitleComp, times));

        String chatRaw = isWorldForce
                ? plugin.getConfig().getString(
                        configPrefix + ".messages.locked-chat-world",
                        "%prefix_error%%c_error%<b>ʟᴏᴄᴋᴇᴅ</b> %c_muted%» %c_text%Cooldown is forced to %c_primary%<b>%forced%s</b> %c_text%in this world."
                  )
                : plugin.getConfig().getString(
                        configPrefix + ".messages.locked-chat-global",
                        "%prefix_error%%c_error%<b>ʟᴏᴄᴋᴇᴅ</b> %c_muted%» %c_text%Cooldown is forced globally to %c_primary%<b>%forced%s</b>%c_text%."
                  );

        player.sendMessage(plugin.parseMessage(
                chatRaw.replace("%forced%", String.valueOf(forcedSec))
                       .replace("%world%", player.getWorld().getName())
        ));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("pearl".startsWith(input)) completions.add("pearl");
            if ("mace".startsWith(input)) completions.add("mace");
            if (sender.hasPermission("vcarterutils.admin") && "admin".startsWith(input)) completions.add("admin");
        } else if (args.length == 2) {
            String input = args[1].toLowerCase();
            if (args[0].equalsIgnoreCase("pearl")) {
                for (int opt : plugin.getPearlManager().getAvailableOptions()) {
                    String s = String.valueOf(opt);
                    if (s.startsWith(input)) completions.add(s);
                }
            } else if (args[0].equalsIgnoreCase("mace")) {
                for (int opt : plugin.getMaceManager().getAvailableOptions()) {
                    String s = String.valueOf(opt);
                    if (s.startsWith(input)) completions.add(s);
                }
            } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("vcarterutils.admin")) {
                for (String sub : List.of("combat", "pearl", "mace", "interrupt", "world", "global")) {
                    if (sub.startsWith(input)) completions.add(sub);
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("vcarterutils.admin")) {
            String input = args[2].toLowerCase();
            for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
                if (w.getName().toLowerCase().startsWith(input)) completions.add(w.getName());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("vcarterutils.admin")) {
            String sub = args[1].toLowerCase();
            String input = args[3].toLowerCase();
            if (sub.equals("combat")) {
                for (int opt : plugin.getCombatTagManager().getAvailableOptions()) {
                    String s = String.valueOf(opt);
                    if (s.startsWith(input)) completions.add(s);
                }
                if ("disable".startsWith(input)) completions.add("disable");
                if ("off".startsWith(input)) completions.add("off");
            } else if (sub.equals("pearl")) {
                for (int opt : plugin.getPearlManager().getAvailableOptions()) {
                    String s = String.valueOf(opt);
                    if (s.startsWith(input)) completions.add(s);
                }
                if ("off".startsWith(input)) completions.add("off");
            } else if (sub.equals("mace")) {
                for (int opt : plugin.getMaceManager().getAvailableOptions()) {
                    String s = String.valueOf(opt);
                    if (s.startsWith(input)) completions.add(s);
                }
                if ("off".startsWith(input)) completions.add("off");
            } else if (sub.equals("interrupt")) {
                for (String s : List.of("on", "off", "default")) {
                    if (s.startsWith(input)) completions.add(s);
                }
            }
        }
        return completions;
    }
}
