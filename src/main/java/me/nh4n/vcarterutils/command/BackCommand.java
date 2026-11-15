package me.nh4n.vcarterutils.command;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.hook.FoCombatTagHook;
import me.nh4n.vcarterutils.manager.BackManager;
import me.nh4n.vcarterutils.manager.LobbyHotbarManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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

        executeBack(player);
        return true;
    }

    /**
     * Executes the back teleportation logic directly.
     * Can be invoked from CommandExecutor, PlayerCommandPreprocessEvent, or LobbyHotbar.
     */
    public void executeBack(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        BackManager manager = plugin.getBackManager();
        if (!manager.isEnabled()) {
            return;
        }

        if (manager.isWorldBlacklisted(player.getWorld())) {
            sendMsg(player, "back.messages.world-blacklisted", "%prefix_warning%%c_error%<b>ʀᴇsᴛʀɪᴄᴛᴇᴅ</b> %c_muted%» %c_text%You cannot use %c_primary%/back %c_text%in this world.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        Location deathLoc = manager.getValidDeathLocation(player);

        if (deathLoc == null || deathLoc.getWorld() == null) {
            sendMsg(player, "back.messages.no-death-location", "%prefix_error%%c_error%<b>ɴᴏ ʟᴏᴄᴀᴛɪᴏɴ</b> %c_muted%» %c_text%No previous death location found.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (manager.isWorldBlacklisted(deathLoc.getWorld())) {
            sendMsg(player, "back.messages.world-blacklisted", "%prefix_warning%%c_error%<b>ʀᴇsᴛʀɪᴄᴛᴇᴅ</b> %c_muted%» %c_text%You cannot use %c_primary%/back %c_text%in this world.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Prevent falling into the void if died below world bottom
        Location targetLoc = deathLoc.clone();
        if (targetLoc.getY() < targetLoc.getWorld().getMinHeight()) {
            targetLoc.setY(targetLoc.getWorld().getMinHeight() + 2);
        }

        // Untag player and end duel combat so nothing blocks the teleport
        plugin.getCombatTagManager().endCombat(player.getUniqueId());
        FoCombatTagHook.untag(player);

        // Mark pending back teleport so PlayerTeleportEvent will be protected from external cancellation
        manager.setPendingBackTeleport(player.getUniqueId(), true);

        player.teleportAsync(targetLoc).thenAccept(success -> {
            manager.setPendingBackTeleport(player.getUniqueId(), false);
            if (Boolean.TRUE.equals(success)) {
                onTeleportSuccess(player, manager);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    manager.setPendingBackTeleport(player.getUniqueId(), true);
                    boolean syncSuccess = player.teleport(targetLoc);
                    manager.setPendingBackTeleport(player.getUniqueId(), false);
                    if (syncSuccess) {
                        onTeleportSuccess(player, manager);
                    } else {
                        sendMsg(player, "back.messages.teleport-failed", "%prefix_error%%c_error%<b>ꜰᴀɪʟᴇᴅ</b> %c_muted%» %c_text%Teleportation failed or was blocked.");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                });
            }
        });
    }

    private void onTeleportSuccess(Player player, BackManager manager) {
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        sendMsg(player, "back.messages.teleported", "<white><sprite:items:item/recovery_compass_16></white> %c_success%Teleported to your last death location.");
        manager.consumeDeathLocation(player.getUniqueId());

        LobbyHotbarManager lobby = plugin.getLobbyHotbarManager();
        if (lobby.isLobbyWorld(player.getWorld())) {
            lobby.applyLobbyHotbar(player);
        } else {
            lobby.restorePlayerHotbar(player);
        }
    }

    private void sendMsg(Player player, String configPath, String defaultMessage) {
        String raw = plugin.getConfig().getString(configPath, defaultMessage);
        if (raw != null && !raw.isEmpty()) {
            player.sendMessage(plugin.parseMessage(raw));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
