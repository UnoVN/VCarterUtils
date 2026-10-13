package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class BackListener implements Listener {

    private final VCarterUtils plugin;

    public BackListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        BackManager manager = plugin.getBackManager();

        if (!manager.isEnabled()) {
            return;
        }

        Location deathLoc = player.getLocation();
        // Luu vi tri tu tran
        manager.recordDeathLocation(player.getUniqueId(), deathLoc);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        BackManager manager = plugin.getBackManager();

        if (!manager.isEnabled()) {
            return;
        }

        // Delay 10 tick de dam bao man hinh client render the gioi hoan tat sau khi hoi sinh
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            Location deathLoc = manager.getDeathLocation(player.getUniqueId());
            if (deathLoc == null) {
                return;
            }

            // Neu ca world chet lan world hien tai deu khong bi blacklist thi gui thong bao
            if (!manager.isWorldBlacklisted(deathLoc.getWorld()) && !manager.isWorldBlacklisted(player.getWorld())) {
                String raw = plugin.getConfig().getString(
                        "back.messages.death-notify",
                        "<glyph:warning_blue> <#a6adc8>You died! Click <click:run_command:'/back'><hover:show_text:'<#a6e3a1>Click to teleport back'><#89dceb><b>[HERE]</b></hover></click> or type <#89dceb><b>/back</b><#a6adc8> to return."
                );
                player.sendMessage(MiniMessage.miniMessage().deserialize(raw));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
            }
        }, 10L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getBackManager().clearDeathLocation(event.getPlayer().getUniqueId());
    }
}
