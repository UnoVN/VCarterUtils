package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.ScoreManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KillDeathListener implements Listener {

    private final VCarterUtils plugin;
    // Bo nho dem luu tin nhan cho nguoi chet sau khi bam respawn
    private final Map<UUID, Component> pendingDeathMessages = new ConcurrentHashMap<>();

    public KillDeathListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Kiem tra hop le: bi ha boi nguoi choi khac va khong phai tu sat
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        ScoreManager manager = plugin.getScoreManager();
        manager.recordKill(killer.getUniqueId(), victim.getUniqueId());

        int killerScore = manager.getKillsAgainst(killer.getUniqueId(), victim.getUniqueId());
        int victimScore = manager.getKillsAgainst(victim.getUniqueId(), killer.getUniqueId());

        // Dinh dang Action Bar: &c<Killer> X - Y &b<Victim>
        String messageRaw = "&c" + killer.getName() + " " + killerScore + " - " + victimScore + " &b" + victim.getName();
        Component actionBarComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(messageRaw);

        // Nguoi thang con song -> Nhan Action Bar ngay lap tuc
        killer.sendActionBar(actionBarComponent);

        // Nguoi thua dang o man hinh chet -> Luu lai de gui sau khi bam hoi sinh
        pendingDeathMessages.put(victim.getUniqueId(), actionBarComponent);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Component pendingMessage = pendingDeathMessages.remove(player.getUniqueId());

        if (pendingMessage != null) {
            // Delay 7 tick de dam bao client da render xong the gioi sau khi respawn
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendActionBar(pendingMessage);
                }
            }, 7L);
        }
    }
}
