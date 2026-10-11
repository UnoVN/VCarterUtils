package me.nh4n.vcarterutils.command;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.ScoreManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

    private static final TextColor COLOR_SKY      = TextColor.fromHexString("#89dceb");
    private static final TextColor COLOR_GREEN    = TextColor.fromHexString("#a6e3a1");
    private static final TextColor COLOR_RED      = TextColor.fromHexString("#f38ba8");
    private static final TextColor COLOR_SUBTEXT  = TextColor.fromHexString("#a6adc8");
    private static final TextColor COLOR_OVERLAY  = TextColor.fromHexString("#6c7086");

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

        // 1. Lenh Reload Config: /score reload
        if (sub.equals("reload")) {
            if (!sender.hasPermission("vcarterutils.admin")) {
                sendMessage(sender, getPrefix().append(Component.text("You do not have permission to execute this command.", COLOR_RED)));
                return;
            }

            plugin.reloadPluginConfig();
            String successMsg = plugin.getConfig().getString(
                    "admin.messages.reload-success",
                    "<glyph:warning_blue> <#a6e3a1>VCarterUtils configuration and regear whitelist reloaded successfully!"
            );
            sender.sendMessage(miniMessage.deserialize(successMsg));
            return;
        }

        // 2. Lenh Admin Reset All: /score resetall
        if (sub.equals("resetall") || sub.equals("clearall")) {
            if (!sender.hasPermission("vcarterutils.admin")) {
                sendMessage(sender, getPrefix().append(Component.text("You do not have permission to execute this command.", COLOR_RED)));
                return;
            }

            manager.clearAll();
            Component broadcastComp = getPrefix().append(Component.text("All 1v1 scores have been globally reset by an administrator.", COLOR_GREEN));
            Bukkit.broadcast(broadcastComp);
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be executed by players."));
            return;
        }

        // 3. Lenh gui yeu cau reset: /score reset <player>
        if (sub.equals("reset")) {
            if (args.length < 2) {
                sendUsage(player);
                return;
            }

            Player target = findPlayerCaseInsensitive(args[1]);
            if (target == null || !target.isOnline()) {
                sendMessage(player, getPrefix()
                        .append(Component.text("Player ", COLOR_RED))
                        .append(Component.text(args[1], COLOR_SKY))
                        .append(Component.text(" is not online.", COLOR_RED)));
                return;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                sendMessage(player, getPrefix().append(Component.text("You cannot send a reset request to yourself.", COLOR_RED)));
                return;
            }

            long timeout = plugin.getConfig().getLong("score.request-timeout-seconds", 60L);
            manager.createRequest(player.getUniqueId(), target.getUniqueId(), timeout);

            sendMessage(player, getPrefix()
                    .append(Component.text("Sent a 1v1 score reset request to ", COLOR_GREEN))
                    .append(Component.text(target.getName(), COLOR_SKY, TextDecoration.BOLD))
                    .append(Component.text(". Waiting for approval...", COLOR_GREEN)));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);

            sendInteractiveRequestMessage(target, player);
            return;
        }

        // 4. Lenh chap nhan yeu cau: /score accept <player>
        if (sub.equals("accept")) {
            if (args.length < 2) {
                sendUsage(player);
                return;
            }

            Player requester = findPlayerCaseInsensitive(args[1]);
            if (requester == null || !manager.hasValidRequest(requester.getUniqueId(), player.getUniqueId())) {
                sendMessage(player, getPrefix()
                        .append(Component.text("You have no pending reset request from ", COLOR_RED))
                        .append(Component.text(args[1], COLOR_SKY))
                        .append(Component.text(".", COLOR_RED)));
                return;
            }

            manager.removeRequest(requester.getUniqueId(), player.getUniqueId());
            manager.resetScoresBetween(requester.getUniqueId(), player.getUniqueId());

            Component successMsg = getPrefix()
                    .append(Component.text("1v1 score between ", COLOR_GREEN))
                    .append(Component.text(player.getName(), COLOR_SKY, TextDecoration.BOLD))
                    .append(Component.text(" and ", COLOR_GREEN))
                    .append(Component.text(requester.getName(), COLOR_SKY, TextDecoration.BOLD))
                    .append(Component.text(" has been reset!", COLOR_GREEN));

            player.sendMessage(successMsg);
            requester.sendMessage(successMsg);

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            requester.playSound(requester.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            return;
        }

        // 5. Lenh tu choi yeu cau: /score decline <player>
        if (sub.equals("decline") || sub.equals("deny")) {
            if (args.length < 2) {
                sendUsage(player);
                return;
            }

            Player requester = findPlayerCaseInsensitive(args[1]);
            if (requester != null && manager.hasValidRequest(requester.getUniqueId(), player.getUniqueId())) {
                manager.removeRequest(requester.getUniqueId(), player.getUniqueId());

                sendMessage(requester, getPrefix()
                        .append(Component.text("Reset request with ", COLOR_RED))
                        .append(Component.text(player.getName(), COLOR_SKY))
                        .append(Component.text(" was declined.", COLOR_RED)));
                requester.playSound(requester.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }

            sendMessage(player, getPrefix()
                    .append(Component.text("Reset request with ", COLOR_RED))
                    .append(Component.text(args[1], COLOR_SKY))
                    .append(Component.text(" was declined.", COLOR_RED)));
            return;
        }

        sendUsage(sender);
    }

    private void sendInteractiveRequestMessage(Player target, Player sender) {
        Component acceptButton = Component.text("[ACCEPT]", COLOR_GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/score accept " + sender.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to accept reset request", COLOR_GREEN)));

        Component declineButton = Component.text("[DECLINE]", COLOR_RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/score decline " + sender.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to decline reset request", COLOR_RED)));

        Component message = Component.text()
                .append(getPrefix())
                .append(Component.text(sender.getName(), COLOR_SKY, TextDecoration.BOLD))
                .append(Component.text(" has requested to reset your 1v1 score! ", COLOR_SUBTEXT))
                .append(acceptButton)
                .append(Component.text(" "))
                .append(declineButton)
                .build();

        target.sendMessage(message);
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
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

    private Component getPrefix() {
        return miniMessage.deserialize("<glyph:warning_blue> ");
    }

    private void sendMessage(CommandSender sender, Component comp) {
        sender.sendMessage(comp);
    }

    private void sendUsage(CommandSender sender) {
        Component usage = getPrefix()
                .append(Component.text("Usage: ", COLOR_SUBTEXT))
                .append(Component.text("/score reset <player>", COLOR_SKY))
                .append(Component.text(" | ", COLOR_OVERLAY))
                .append(Component.text("/score accept <player>", COLOR_SKY))
                .append(Component.text(" | ", COLOR_OVERLAY))
                .append(Component.text("/score resetall", COLOR_SKY));
        sender.sendMessage(usage);
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
