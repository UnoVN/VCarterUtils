package me.nh4n.vcarterutils.command;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.ScoreManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ScoreCommand implements CommandExecutor, TabCompleter, Listener {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    public ScoreCommand(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().trim();
        String lower = msg.toLowerCase();

        if (lower.equals("/sc") || lower.startsWith("/sc ")) {
            event.setCancelled(true);

            String[] rawParts = msg.substring(1).trim().split("\\s+");
            String[] args = new String[Math.max(0, rawParts.length - 1)];
            if (rawParts.length > 1) {
                System.arraycopy(rawParts, 1, args, 0, args.length);
            }

            handleScoreLogic(event.getPlayer(), args);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        handleScoreLogic(sender, args);
        return true;
    }

    private void handleScoreLogic(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return;
        }

        String sub = args[0].toLowerCase();
        ScoreManager manager = plugin.getScoreManager();

        // 1. /score reload
        if (sub.equals("reload")) {
            if (!sender.hasPermission("vcarterutils.admin")) {
                sendNoPerm(sender);
                return;
            }

            plugin.reloadPluginConfig();
            String successMsg = plugin.getConfig().getString(
                    "admin.messages.reload-success",
                    "<glyph:warning_blue> <white><sprite:items:item/comparator></white> <#a6e3a1>VCarterUtils reloaded!"
            );
            sender.sendMessage(miniMessage.deserialize(successMsg));
            return;
        }

        // 2. /score resetall
        if (sub.equals("resetall") || sub.equals("clearall")) {
            if (!sender.hasPermission("vcarterutils.admin")) {
                sendNoPerm(sender);
                return;
            }

            manager.clearAll();
            String raw = plugin.getConfig().getString(
                    "score.messages.global-reset-admin",
                    "<glyph:warning_blue> <white><sprite:items:item/diamond_sword></white> <#a6e3a1>All 1v1 scores reset by Admin."
            );
            Bukkit.broadcast(miniMessage.deserialize(raw));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only."));
            return;
        }

        // 3. /score reset <player>
        if (sub.equals("reset")) {
            if (args.length < 2) {
                sendUsage(player);
                return;
            }

            Player target = findPlayerCaseInsensitive(args[1]);
            if (target == null || !target.isOnline()) {
                String raw = plugin.getConfig().getString(
                        "score.messages.player-not-found",
                        "<glyph:warning_blue> <white><sprite:items:item/diamond_sword></white> <#f38ba8>Player <#89dceb>%target% <#f38ba8>is offline."
                );
                player.sendMessage(miniMessage.deserialize(raw.replace("%target%", args[1])));
                return;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                String raw = plugin.getConfig().getString(
                        "score.messages.cannot-target-self",
                        "<glyph:warning_blue> <white><sprite:items:item/diamond_sword></white> <#f38ba8>Cannot target yourself."
                );
                player.sendMessage(miniMessage.deserialize(raw));
                return;
            }

            long timeout = plugin.getConfig().getLong("score.request-timeout-seconds", 60L);
            manager.createRequest(player.getUniqueId(), target.getUniqueId(), timeout);

            String sentRaw = plugin.getConfig().getString(
                    "score.messages.request-sent",
                    "<glyph:warning_blue> <white><sprite:items:item/diamond_sword></white> <#a6e3a1>Reset request sent to <#89dceb><b>%target%</b>"
            );
            player.sendMessage(miniMessage.deserialize(sentRaw.replace("%target%", target.getName())));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);

            String recvRaw = plugin.getConfig().getString(
                    "score.messages.request-received",
                    "<glyph:warning_blue> <white><sprite:items:item/diamond_sword></white> <#89dceb><b>%sender%</b> <#a6adc8>wants to reset 1v1 score: <click:run_command:'/score accept %sender%'><hover:show_text:'<#a6e3a1>Click to accept'><#a6e3a1><b>[ACCEPT]</b></hover></click> <click:run_command:'/score decline %sender%'><hover:show_text:'<#f38ba8>Click to decline'><#f38ba8><b>[DECLINE]</b></hover></click>"
            );
            target.sendMessage(miniMessage.deserialize(recvRaw.replace("%sender%", player.getName())));
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            return;
        }

        // 4. /score accept <player>
        if (sub.equals("accept")) {
            if (args.length < 2) {
                sendUsage(player);
                return;
            }

            Player requester = findPlayerCaseInsensitive(args[1]);
            if (requester == null || !manager.hasValidRequest(requester.getUniqueId(), player.getUniqueId())) {
                String raw = plugin.getConfig().getString(
                        "score.messages.no-pending-request",
                        "<glyph:warning_blue> <white><sprite:items:item/diamond_sword></white> <#f38ba8>No pending request from <#89dceb>%sender%"
                );
                player.sendMessage(miniMessage.deserialize(raw.replace("%sender%", args[1])));
                return;
            }

            manager.removeRequest(requester.getUniqueId(), player.getUniqueId());
            manager.resetScoresBetween(requester.getUniqueId(), player.getUniqueId());

            String successRaw = plugin.getConfig().getString(
                    "score.messages.score-reset-success",
                    "<glyph:warning_blue> <white><sprite:items:item/diamond_sword></white> <#a6e3a1>Score reset: <#89dceb><b>%player1%</b> <#6c7086>vs <#89dceb><b>%player2%</b>"
            );
            Component successMsg = miniMessage.deserialize(
                    successRaw.replace("%player1%", player.getName()).replace("%player2%", requester.getName())
            );

            player.sendMessage(successMsg);
            requester.sendMessage(successMsg);

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            requester.playSound(requester.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            return;
        }

        // 5. /score decline <player>
        if (sub.equals("decline") || sub.equals("deny")) {
            if (args.length < 2) {
                sendUsage(player);
                return;
            }

            String declineRaw = plugin.getConfig().getString(
                    "score.messages.request-declined",
                    "<glyph:warning_blue> <white><sprite:items:item/diamond_sword></white> <#f38ba8>Reset request with <#89dceb>%player% <#f38ba8>declined."
            );

            Player requester = findPlayerCaseInsensitive(args[1]);
            if (requester != null && manager.hasValidRequest(requester.getUniqueId(), player.getUniqueId())) {
                manager.removeRequest(requester.getUniqueId(), player.getUniqueId());
                requester.sendMessage(miniMessage.deserialize(declineRaw.replace("%player%", player.getName())));
                requester.playSound(requester.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }

            player.sendMessage(miniMessage.deserialize(declineRaw.replace("%player%", args[1])));
            return;
        }

        sendUsage(sender);
    }

    private Player findPlayerCaseInsensitive(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) {
                return online;
            }
        }
        return null;
    }

    private void sendNoPerm(CommandSender sender) {
        String raw = plugin.getConfig().getString(
                "score.messages.no-permission",
                "<glyph:warning_blue> <#f38ba8>No permission."
        );
        sender.sendMessage(miniMessage.deserialize(raw));
    }

    private void sendUsage(CommandSender sender) {
        String raw = plugin.getConfig().getString(
                "score.messages.usage",
                "<glyph:warning_blue> <white><sprite:items:item/diamond_sword></white> <#89dceb>/score reset <player> <#6c7086>| <#89dceb>accept <player> <#6c7086>| <#89dceb>resetall"
        );
        sender.sendMessage(miniMessage.deserialize(raw));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> subs = new ArrayList<>(List.of("reset", "accept", "decline"));
            if (sender.hasPermission("vcarterutils.admin")) {
                subs.add("resetall");
                subs.add("reload");
            }
            for (String sub : subs) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("reset") ||
                                 args[0].equalsIgnoreCase("accept") ||
                                 args[0].equalsIgnoreCase("decline"))) {
            String input = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().equalsIgnoreCase(sender.getName()) && p.getName().toLowerCase().startsWith(input)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }

        return completions;
    }
}
