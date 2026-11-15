package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.PearlManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PearlListener implements Listener {

    private final VCarterUtils plugin;

    public PearlListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    /**
     * When a player launches an Ender Pearl:
     * Vanilla automatically applies 20 ticks (1s) right after ProjectileLaunchEvent.
     * We run a task on the next tick to override the cooldown with the exact configured duration
     * using the native Vanilla cooldown overlay.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) {
            return;
        }
        if (!(pearl.getShooter() instanceof Player player)) {
            return;
        }

        PearlManager manager = plugin.getPearlManager();
        if (!manager.isEnabled()) {
            return;
        }

        int cooldownSeconds = manager.getEffectiveCooldownSeconds(player);
        int cooldownTicks = Math.max(0, cooldownSeconds * 20);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.setCooldown(Material.ENDER_PEARL, cooldownTicks);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        PearlManager manager = plugin.getPearlManager();
        if (manager.isEnabled() && manager.isClearOnRespawn()) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    manager.clearCooldown(player);
                }
            }, 2L);
        }
    }
}
