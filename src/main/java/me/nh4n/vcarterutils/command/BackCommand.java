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

        if (manager.isWorldBlacklisted(player.getWorld())) {
            sendMsg(player, "back.messages.world-blacklisted");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        Location deathLoc = manager.getDeathLocation(player.getUniqueId());
        if (deathLoc == null || deathLoc.getWorld() == null) {
            sendMsg(player, "back.messages.no-death-location");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        if (manager.isWorldBlacklisted(deathLoc.getWorld())) {
            sendMsg(player, "back.messages.world-blacklisted");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        player.teleportAsync(deathLoc).thenAccept(success -> {
            if (success) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                sendMsg(player, "back.messages.teleported");
                manager.clearDeathLocation(player.getUniqueId());

                // Neu nguoi choi back ve mot vi tri van nam trong Lobby World, cap nhat lai Lobby Hotbar de an item /back
                if (plugin.getLobbyHotbarManager().isLobbyWorld(player.getWorld())) {
                    plugin.getLobbyHotbarManager().applyLobbyHotbar(player);
                }
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
