package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.hook.FoCombatTagHook;
import me.nh4n.vcarterutils.manager.BackManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class BackListener implements Listener {

    private final VCarterUtils plugin;

    public BackListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    /**
     * Intercepts /back and aliases at LOWEST priority before EssentialsX, CMI, or any other plugin.
     * Guarantees VCarterUtils always handles /back directly and instantly.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().trim();
        String lower = msg.toLowerCase();
        if (lower.equals("/back") || lower.startsWith("/back ")
                || lower.equals("/deathback") || lower.startsWith("/deathback ")
                || lower.equals("/return") || lower.startsWith("/return ")
                || lower.equals("/vcarterutils:back") || lower.startsWith("/vcarterutils:back ")) {
            event.setCancelled(true);
            if (plugin.getBackCommand() != null) {
                plugin.getBackCommand().executeBack(event.getPlayer());
            }
        }
    }

    /**
     * Protects the /back teleport from being cancelled by other plugins (e.g. FoCombatTag or Essentials).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (plugin.getBackManager().isPendingBackTeleport(player.getUniqueId())) {
            if (event.isCancelled()) {
                event.setCancelled(false); // Uncancel back teleport
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        BackManager manager = plugin.getBackManager();

        if (manager.isEnabled()) {
            Location deathLoc = player.getLocation();
            if (deathLoc.getWorld() != null) {
                if (deathLoc.getY() < deathLoc.getWorld().getMinHeight()) {
                    deathLoc = deathLoc.clone();
                    deathLoc.setY(deathLoc.getWorld().getMinHeight() + 2);
                }
                manager.recordDeathLocation(player.getUniqueId(), deathLoc);
            }
        }

        // On death: clear combat tag and duel tracking so /back is not blocked
        FoCombatTagHook.untag(player);
        plugin.getCombatTagManager().endCombat(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        BackManager manager = plugin.getBackManager();

        // Ensure lingering combat tag is cleared on respawn
        FoCombatTagHook.untag(player);
        plugin.getCombatTagManager().endCombat(player.getUniqueId());

        if (!manager.isEnabled()) {
            return;
        }

        // Delay 10 ticks to ensure client world rendering is complete after respawn
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            Location deathLoc = manager.getValidDeathLocation(player);
            if (deathLoc == null || deathLoc.getWorld() == null) {
                return;
            }

            // Only notify if neither death world nor current world is blacklisted
            if (!manager.isWorldBlacklisted(deathLoc.getWorld()) && !manager.isWorldBlacklisted(player.getWorld())) {
                String raw = plugin.getConfig().getString(
                        "back.messages.death-notify",
                        "<white><sprite:items:item/recovery_compass_16></white> %c_text%You died! Click <click:run_command:'/back'><hover:show_text:'<#a6e3a1>Click to teleport to death location'>%c_primary%<b>[« ʀᴇᴛᴜʀɴ]</b></hover></click> %c_text%or type %c_primary%/back%c_text%."
                );
                player.sendMessage(plugin.parseMessage(raw));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
            }
        }, 10L);
    }
}
