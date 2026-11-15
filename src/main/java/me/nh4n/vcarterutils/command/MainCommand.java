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
                        "%prefix_error%%c_error%<b>ɴᴏ ᴘᴇʀᴍɪssɪᴏɴ</b> %c_muted%» %c_text%You do not have permission."
                );
                sender.sendMessage(plugin.parseMessage(noPerm));
                return true;
            }

            plugin.reloadPluginConfig();

            String successMsg = plugin.getConfig().getString(
                    "admin.messages.reload-success",
                    "%prefix_admin%%c_success%<b>ʀᴇʟᴏᴀᴅᴇᴅ</b> %c_muted%» %c_text%VCarterUtils configuration synchronized."
            );
            sender.sendMessage(plugin.parseMessage(successMsg));
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("setlobby") || args[0].equalsIgnoreCase("setlobbyworld"))) {
            if (!sender.hasPermission("vcarterutils.admin")) {
                String noPerm = plugin.getConfig().getString(
                        "score.messages.no-permission",
                        "%prefix_error%%c_error%<b>ɴᴏ ᴘᴇʀᴍɪssɪᴏɴ</b> %c_muted%» %c_text%You do not have permission."
                );
                sender.sendMessage(plugin.parseMessage(noPerm));
                return true;
            }

            String targetWorld;
            if (args.length >= 2) {
                targetWorld = args[1];
            } else if (sender instanceof Player player) {
                targetWorld = player.getWorld().getName();
            } else {
                sender.sendMessage(plugin.parseMessage("%prefix_error%%c_text%ᴜsᴀɢᴇ %c_muted%» %c_primary%/" + label + " setlobby <world>"));
                return true;
            }

            boolean added = plugin.getLobbyHotbarManager().toggleLobbyWorld(targetWorld);
            if (added) {
                String msg = plugin.getConfig().getString(
                        "lobby-hotbar.messages.world-added",
                        "%prefix_admin%%c_success%Added %c_primary%<b>%world%</b> %c_success%to Lobby Hotbar worlds."
                );
                sender.sendMessage(plugin.parseMessage(msg.replace("%world%", targetWorld)));
            } else {
                String msg = plugin.getConfig().getString(
                        "lobby-hotbar.messages.world-removed",
                        "%prefix_admin%%c_error%Removed %c_primary%<b>%world%</b> %c_error%from Lobby Hotbar worlds."
                );
                sender.sendMessage(plugin.parseMessage(msg.replace("%world%", targetWorld)));
            }
            return true;
        }

        // /vcu combat <world> <seconds|off|disable>
        if (args.length > 0 && (args[0].equalsIgnoreCase("combat") || args[0].equalsIgnoreCase("combattag"))) {
            if (!sender.hasPermission("vcarterutils.admin")) {
                sender.sendMessage(plugin.parseMessage("%prefix_error%%c_error%<b>ɴᴏ ᴘᴇʀᴍɪssɪᴏɴ</b> %c_muted%» %c_text%You do not have permission."));
                return true;
            }

            if (args.length < 3) {
                sender.sendMessage(plugin.parseMessage("%prefix_error%%c_text%ᴜsᴀɢᴇ %c_muted%» %c_primary%/" + label + " combat <world> <seconds|off|disable>"));
                return true;
            }

            String targetWorld = args[1];
            String valStr = args[2].toLowerCase();
            var combatManager = plugin.getCombatTagManager();

            if (valStr.equals("off")) {
                combatManager.setWorldForceSeconds(targetWorld, -1);
                sender.sendMessage(plugin.parseMessage("%prefix_admin%%c_primary%<b>" + targetWorld + "</b> %c_muted%» %c_text%Combat Tag: <#f38ba8><b>✖ ᴏꜰꜰ (ᴅᴇꜰᴀᴜʟᴛ)</b>"));
            } else if (valStr.equals("disable") || valStr.equals("disabled") || valStr.equals("0")) {
                combatManager.setWorldForceSeconds(targetWorld, 0);
                sender.sendMessage(plugin.parseMessage("%prefix_admin%%c_primary%<b>" + targetWorld + "</b> %c_muted%» %c_text%Combat Tag: <#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ (0s)</b>"));
            } else {
                try {
                    int sec = Integer.parseInt(valStr);
                    combatManager.setWorldForceSeconds(targetWorld, sec);
                    sender.sendMessage(plugin.parseMessage("%prefix_admin%%c_primary%<b>" + targetWorld + "</b> %c_muted%» %c_text%Combat Tag set to %c_primary%<b>" + sec + "s</b>%c_text%."));
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.parseMessage("%prefix_error%%c_error%<b>ɪɴᴠᴀʟɪᴅ</b> %c_muted%» %c_text%Use a number, %c_primary%disable%c_text%, or %c_primary%off%c_text%."));
                    return true;
                }
            }
            return true;
        }

        // /vcu blockedcommands <add|remove|list> [world|global] [command]
        if (args.length > 0 && (args[0].equalsIgnoreCase("blockedcommands") || args[0].equalsIgnoreCase("blockedcmd") || args[0].equalsIgnoreCase("blockcmd"))) {
            if (!sender.hasPermission("vcarterutils.admin")) {
                sender.sendMessage(plugin.parseMessage("%prefix_error%%c_error%<b>ɴᴏ ᴘᴇʀᴍɪssɪᴏɴ</b> %c_muted%» %c_text%You do not have permission."));
                return true;
            }

            var combatManager = plugin.getCombatTagManager();
            if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
                String targetScope = (args.length >= 3) ? args[2] : "global";
                List<String> list = targetScope.equalsIgnoreCase("global")
                        ? combatManager.getGlobalBlockedCommands()
                        : combatManager.getWorldBlockedCommands(targetScope);
                sender.sendMessage(plugin.parseMessage("%prefix_admin%%c_primary%<b>ʙʟᴏᴄᴋᴇᴅ (" + targetScope + ")</b> %c_muted%» %c_text%" + String.join(", ", list)));
                return true;
            }

            if (args.length >= 4 && args[1].equalsIgnoreCase("add")) {
                String targetScope = args[2];
                String cmd = args[3];
                combatManager.addBlockedCommand(targetScope, cmd);
                sender.sendMessage(plugin.parseMessage("%prefix_admin%%c_success%Added %c_primary%/" + cmd + " %c_success%to blocked commands for %c_primary%<b>" + targetScope + "</b>%c_success%."));
                return true;
            }

            if (args.length >= 4 && args[1].equalsIgnoreCase("remove")) {
                String targetScope = args[2];
                String cmd = args[3];
                boolean removed = combatManager.removeBlockedCommand(targetScope, cmd);
                if (removed) {
                    sender.sendMessage(plugin.parseMessage("%prefix_admin%%c_error%Removed %c_primary%/" + cmd + " %c_error%from blocked commands for %c_primary%<b>" + targetScope + "</b>%c_error%."));
                } else {
                    sender.sendMessage(plugin.parseMessage("%prefix_error%%c_text%Command %c_primary%/" + cmd + " %c_text%was not in blocked list for %c_primary%<b>" + targetScope + "</b>%c_text%."));
                }
                return true;
            }

            sender.sendMessage(plugin.parseMessage("%prefix_info%%c_text%ᴜsᴀɢᴇ %c_muted%» %c_primary%/" + label + " blockedcommands <add|remove|list> <global|world> [command]"));
            return true;
        }

        sender.sendMessage(plugin.parseMessage(
                "%prefix_info%%c_primary%<b>ᴠᴄᴀʀᴛᴇʀᴜᴛɪʟs</b> %c_muted%» %c_primary%/" + label + " reload %c_muted%│ %c_primary%combat <world> <sec|off|disable> %c_muted%│ %c_primary%setlobby [world]"
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
            for (String sub : List.of("reload", "setlobby", "combat", "blockedcommands")) {
                if (sub.startsWith(input)) completions.add(sub);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String input = args[1].toLowerCase();
            if (sub.equals("setlobby") || sub.equals("combat")) {
                for (World w : Bukkit.getWorlds()) {
                    if (w.getName().toLowerCase().startsWith(input)) completions.add(w.getName());
                }
            } else if (sub.equals("blockedcommands")) {
                for (String action : List.of("add", "remove", "list")) {
                    if (action.startsWith(input)) completions.add(action);
                }
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            String input = args[2].toLowerCase();
            if (sub.equals("combat")) {
                for (int opt : plugin.getCombatTagManager().getAvailableOptions()) {
                    String s = String.valueOf(opt);
                    if (s.startsWith(input)) completions.add(s);
                }
                if ("disable".startsWith(input)) completions.add("disable");
                if ("off".startsWith(input)) completions.add("off");
            } else if (sub.equals("blockedcommands")) {
                if ("global".startsWith(input)) completions.add("global");
                for (World w : Bukkit.getWorlds()) {
                    if (w.getName().toLowerCase().startsWith(input)) completions.add(w.getName());
                }
            }
        }
        return completions;
    }
}
