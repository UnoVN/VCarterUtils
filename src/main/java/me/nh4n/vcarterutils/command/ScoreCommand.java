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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ScoreCommand implements CommandExecutor, TabCompleter {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    public ScoreCommand(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendConfigMessage(sender, "score.messages.usage");
            return true;
        }

        String sub = args[0].toLowerCase();
        ScoreManager manager = plugin.getScoreManager();

        // 1. Lenh Admin Reset All: /score resetall
        if (sub.equals("resetall") || sub.equals("clearall")) {
            if (!sender.hasPermission("vcarterutils.admin")) {
                sendConfigMessage(sender, "score.messages.no-permission");
                return true;
            }

            manager.clearAll();
            String broadcastMsg = plugin.getConfig().getString("score.messages.global-reset-admin");
            if (broadcastMsg != null) {
                Bukkit.broadcast(miniMessage.deserialize(broadcastMsg));
            }
            return true;
        }

        // Cac lenh duoi day yeu cau phai la Player
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be executed by players."));
            return true;
        }

        // 2. Lenh gui request reset voi player khac: /score reset <player>
        if (sub.equals("reset")) {
            if (args.length < 2) {
                sendConfigMessage(player, "score.messages.usage");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                sendFormatted(player, "score.messages.player-not-found", "%target%", args[1]);
                return true;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                sendConfigMessage(player, "score.messages.cannot-target-self");
                return true;
            }

            long timeout = plugin.getConfig().getLong("score.request-timeout-seconds", 60L);
            manager.createRequest(player.getUniqueId(), target.getUniqueId(), timeout);

            sendFormatted(player, "score.messages.request-sent", "%target%", target.getName());

            // Gui tin nhan kem nut bam cho doi thu
            String rawReceived = plugin.getConfig().getString("score.messages.request-received", "")
                    .replace("%sender%", player.getName());
            target.sendMessage(miniMessage.deserialize(rawReceived));
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            return true;
        }

        // 3. Lenh chap nhan request: /score accept <player>
        if (sub.equals("accept")) {
            if (args.length < 2) {
                sendConfigMessage(player, "score.messages.usage");
                return true;
            }

            Player requester = Bukkit.getPlayerExact(args[1]);
            if (requester == null || !manager.hasValidRequest(requester.getUniqueId(), player.getUniqueId())) {
                sendFormatted(player, "score.messages.no-pending-request", "%sender%", args[1]);
                return true;
            }

            // Xoa request va reset ti so
            manager.removeRequest(requester.getUniqueId(), player.getUniqueId());
            manager.resetScoresBetween(requester.getUniqueId(), player.getUniqueId());

            // Thong bao cho ca 2 nguoi choi
            String successMsg = plugin.getConfig().getString("score.messages.score-reset-success", "")
                    .replace("%player1%", player.getName())
                    .replace("%player2%", requester.getName());
            Component successComp = miniMessage.deserialize(successMsg);

            player.sendMessage(successComp);
            requester.sendMessage(successComp);

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            requester.playSound(requester.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            return true;
        }

        // 4. Lenh tu choi request: /score decline <player>
        if (sub.equals("decline") || sub.equals("deny")) {
            if (args.length < 2) {
                sendConfigMessage(player, "score.messages.usage");
                return true;
            }

            Player requester = Bukkit.getPlayerExact(args[1]);
            if (requester != null && manager.hasValidRequest(requester.getUniqueId(), player.getUniqueId())) {
                manager.removeRequest(requester.getUniqueId(), player.getUniqueId());
                sendFormatted(requester, "score.messages.request-declined", "%player%", player.getName());
            }

            sendFormatted(player, "score.messages.request-declined", "%player%", args[1]);
            return true;
        }

        sendConfigMessage(sender, "score.messages.usage");
        return true;
    }

    private void sendConfigMessage(CommandSender sender, String configPath) {
        String raw = plugin.getConfig().getString(configPath);
        if (raw != null && !raw.isEmpty()) {
            sender.sendMessage(miniMessage.deserialize(raw));
        }
    }

    private void sendFormatted(CommandSender sender, String configPath, String placeholder, String value) {
        String raw = plugin.getConfig().getString(configPath);
        if (raw != null && !raw.isEmpty()) {
            sender.sendMessage(miniMessage.deserialize(raw.replace(placeholder, value)));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> subs = new ArrayList<>(List.of("reset", "accept", "decline"));
            if (sender.hasPermission("vcarterutils.admin")) {
                subs.add("resetall");
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
