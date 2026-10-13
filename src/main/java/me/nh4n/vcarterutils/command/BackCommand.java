package me.nh4n.vcarterutils.command;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.BackManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class BackCommand implements CommandExecutor, TabCompleter {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    public BackCommand(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be executed by players."));
            return true;
        }

        BackManager manager = plugin.getBackManager();
        if (!manager.isEnabled()) {
            return true;
        }

        // 1. Kiem tra xem world hien tai co bi blacklist khong
        if (manager.isWorldBlacklisted(player.getWorld())) {
            sendMsg(player, "back.messages.world-blacklisted");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        // 2. Kiem tra xem co toa do tu tran luu trong bo nho khong
        Location deathLoc = manager.getDeathLocation(player.getUniqueId());
        if (deathLoc == null || deathLoc.getWorld() == null) {
            sendMsg(player, "back.messages.no-death-location");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        // 3. Kiem tra xem world vua chet co bi blacklist khong
        if (manager.isWorldBlacklisted(deathLoc.getWorld())) {
            sendMsg(player, "back.messages.world-blacklisted");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        // 4. Dich chuyen bat dong bo an toan qua Paper API (teleportAsync)
        player.teleportAsync(deathLoc).thenAccept(success -> {
            if (success) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                sendMsg(player, "back.messages.teleported");
                // Xoa toa do sau khi da su dung de tranh spam teleport
                manager.clearDeathLocation(player.getUniqueId());
            }
        });

        return true;
    }

    private void sendMsg(Player player, String configPath) {
        String raw = plugin.getConfig().getString(configPath);
        if (raw != null && !raw.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(raw));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
