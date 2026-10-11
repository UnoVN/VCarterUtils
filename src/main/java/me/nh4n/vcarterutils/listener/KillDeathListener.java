package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.ScoreManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KillDeathListener implements Listener {

    private final VCarterUtils plugin;

    private final Map<UUID, BukkitTask> activeActionBarTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Component> pendingRespawnMessages = new ConcurrentHashMap<>();

    // Bang mau Hex theo yeu cau: &#e64553, &#a6d189, &#04a5e5
    private static final TextColor COLOR_KILLER = TextColor.fromHexString("#e64553");
    private static final TextColor COLOR_SCORE  = TextColor.fromHexString("#a6d189");
    private static final TextColor COLOR_VICTIM = TextColor.fromHexString("#04a5e5");

    public KillDeathListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        ScoreManager manager = plugin.getScoreManager();
        manager.recordKill(killer.getUniqueId(), victim.getUniqueId());

        int killerScore = manager.getKillsAgainst(killer.getUniqueId(), victim.getUniqueId());
        int victimScore = manager.getKillsAgainst(victim.getUniqueId(), killer.getUniqueId());

        // Dinh dang Action Bar: &#e64553<Killer> &l&#a6d189 <Score> - <Score> &r&#04a5e5<Victim>
        Component actionBarComponent = Component.text()
                .append(Component.text(killer.getName(), COLOR_KILLER))
                .append(Component.text(" "))
                .append(Component.text(killerScore + " - " + victimScore, COLOR_SCORE, TextDecoration.BOLD))
                .append(Component.text(" "))
                .append(Component.text(victim.getName(), COLOR_VICTIM))
                .build();

        // Nguoi ha guc con song: Gui lien tuc trong 15 giay (15s)
        startPersistentActionBar(killer, actionBarComponent, 15);

        // Nguoi bi ha guc dang o man hinh chet: Luu lai de hien thi 15s sau khi Respawn
        pendingRespawnMessages.put(victim.getUniqueId(), actionBarComponent);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Component msg = pendingRespawnMessages.remove(player.getUniqueId());

        if (msg != null) {
            // Cho 7 tick sau khi hoi sinh de client render xong the gioi
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    startPersistentActionBar(player, msg, 15);
                }
            }, 7L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        stopActionBarTask(uuid);
        pendingRespawnMessages.remove(uuid);
    }

    /**
     * Gui lai Action Bar moi 20 tick (1 giay) de giu tren man hinh dung 15 giay
     */
    private void startPersistentActionBar(Player player, Component component, int durationSeconds) {
        UUID uuid = player.getUniqueId();
        stopActionBarTask(uuid);

        BukkitTask task = new BukkitRunnable() {
            int secondsPassed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || secondsPassed >= durationSeconds) {
                    activeActionBarTasks.remove(uuid);
                    cancel();
                    return;
                }

                player.sendActionBar(component);
                secondsPassed++;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        activeActionBarTasks.put(uuid, task);
    }

    private void stopActionBarTask(UUID uuid) {
        BukkitTask runningTask = activeActionBarTasks.remove(uuid);
        if (runningTask != null) {
            runningTask.cancel();
        }
    }

    public void cancelAllTasks() {
        for (BukkitTask task : activeActionBarTasks.values()) {
            task.cancel();
        }
        activeActionBarTasks.clear();
        pendingRespawnMessages.clear();
    }
}
