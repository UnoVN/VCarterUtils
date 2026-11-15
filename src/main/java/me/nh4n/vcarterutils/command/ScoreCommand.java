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
                    "%prefix_admin%%c_success%<b>ʀᴇʟᴏᴀᴅᴇᴅ</b> %c_muted%» %c_text%VCarterUtils configuration synchronized."
            );
            sender.sendMessage(plugin.parseMessage(successMsg));
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
                    "%prefix_admin%%c_success%All 1v1 scores have been reset by an admin."
            );
            Bukkit.broadcast(plugin.parseMessage(raw));
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
                        "%prefix_error%%c_error%<b>ɴᴏᴛ ꜰᴏᴜɴᴅ</b> %c_muted%» %c_text%Player %c_primary%%target% %c_text%is not online."
                );
                player.sendMessage(plugin.parseMessage(raw.replace("%target%", args[1])));
                return;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                String raw = plugin.getConfig().getString(
                        "score.messages.cannot-target-self",
                        "%prefix_error%%c_error%<b>ɪɴᴠᴀʟɪᴅ</b> %c_muted%» %c_text%You cannot target yourself."
                );
                player.sendMessage(plugin.parseMessage(raw));
                return;
            }

            long timeout = plugin.getConfig().getLong("score.request-timeout-seconds", 60L);
            manager.createRequest(player.getUniqueId(), target.getUniqueId(), timeout);

            String sentRaw = plugin.getConfig().getString(
                    "score.messages.request-sent",
                    "%prefix_info%%c_text%Sent 1v1 score reset request to %c_primary%<b>%target%</b>%c_text%."
            );
            player.sendMessage(plugin.parseMessage(sentRaw.replace("%target%", target.getName())));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);

            String recvRaw = plugin.getConfig().getString(
                    "score.messages.request-received",
                    "%prefix_info%%c_primary%<b>%sender%</b> %c_text%wants to reset 1v1 score %c_muted%» <click:run_command:'/score accept %sender%'><hover:show_text:'<#a6e3a1>Click to accept reset'>%c_success%<b>[✔ ᴀᴄᴄᴇᴘᴛ]</b></hover></click> <click:run_command:'/score decline %sender%'><hover:show_text:'<#f38ba8>Click to decline reset'>%c_error%<b>[✖ ᴅᴇᴄʟɪɴᴇ]</b></hover></click>"
            );
            target.sendMessage(plugin.parseMessage(recvRaw.replace("%sender%", player.getName())));
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
                        "%prefix_error%%c_error%<b>ᴇxᴘɪʀᴇᴅ</b> %c_muted%» %c_text%No pending request from %c_primary%%sender%%c_text%."
                );
                player.sendMessage(plugin.parseMessage(raw.replace("%sender%", args[1])));
                return;
            }

            manager.removeRequest(requester.getUniqueId(), player.getUniqueId());
            manager.resetScoresBetween(requester.getUniqueId(), player.getUniqueId());

            String successRaw = plugin.getConfig().getString(
                    "score.messages.score-reset-success",
                    "<white><sprite:items:item/netherite_sword></white> %c_success%<b>sᴄᴏʀᴇ ʀᴇsᴇᴛ</b> %c_muted%» %c_primary%<b>%player1%</b> %c_muted%⚔ %c_primary%<b>%player2%</b>"
            );
            Component successMsg = plugin.parseMessage(
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
                    "%prefix_error%%c_error%<b>ᴅᴇᴄʟɪɴᴇᴅ</b> %c_muted%» %c_text%Reset request with %c_primary%%player% %c_text%was declined."
            );

            Player requester = findPlayerCaseInsensitive(args[1]);
            if (requester != null && manager.hasValidRequest(requester.getUniqueId(), player.getUniqueId())) {
                manager.removeRequest(requester.getUniqueId(), player.getUniqueId());
                requester.sendMessage(plugin.parseMessage(declineRaw.replace("%player%", player.getName())));
                requester.playSound(requester.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }

            player.sendMessage(plugin.parseMessage(declineRaw.replace("%player%", args[1])));
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
                "%prefix_error%%c_error%<b>ɴᴏ ᴘᴇʀᴍɪssɪᴏɴ</b> %c_muted%» %c_text%You do not have permission."
        );
        sender.sendMessage(plugin.parseMessage(raw));
    }

    private void sendUsage(CommandSender sender) {
        String raw = plugin.getConfig().getString(
                "score.messages.usage",
                "%prefix_info%%c_text%ᴜsᴀɢᴇ %c_muted%» %c_primary%/score reset <player> %c_muted%│ %c_primary%accept <player> %c_muted%│ %c_primary%resetall"
        );
        sender.sendMessage(plugin.parseMessage(raw));
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
