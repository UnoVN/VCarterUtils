package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.ScoreManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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


    // Bang mau: Killer (#e64553), Ti so (Trang #ffffff), Victim (#04a5e5)
    private static final TextColor COLOR_KILLER = TextColor.fromHexString("#e64553");
    private static final TextColor COLOR_SCORE  = NamedTextColor.WHITE;
    private static final TextColor COLOR_VICTIM = TextColor.fromHexString("#04a5e5");

    public KillDeathListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    public boolean isScoreActive(UUID uuid) {
        if (uuid == null) return false;
        return activeActionBarTasks.containsKey(uuid);
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

        // Format: &#e64553<Killer> &f<Score> - <Score> &#04a5e5<Victim>
        Component actionBarComponent = Component.text()
                .append(Component.text(killer.getName(), COLOR_KILLER))
                .append(Component.text(" "))
                .append(Component.text(killerScore + " - " + victimScore, COLOR_SCORE))
                .append(Component.text(" "))
                .append(Component.text(victim.getName(), COLOR_VICTIM))
                .build();

        int duration = plugin.getConfig().getInt("score.action-bar.duration-seconds", 15);

        // Killer: Display for duration seconds
        startPersistentActionBar(killer, actionBarComponent, duration);

        // Victim: Store until player respawns
        pendingRespawnMessages.put(victim.getUniqueId(), actionBarComponent);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Component msg = pendingRespawnMessages.remove(player.getUniqueId());

        if (msg != null) {
            int duration = plugin.getConfig().getInt("score.action-bar.duration-seconds", 15);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    startPersistentActionBar(player, msg, duration);
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

    private void startPersistentActionBar(Player player, Component component, int durationSeconds) {
        UUID uuid = player.getUniqueId();
        stopActionBarTask(uuid);

        BukkitTask task = new BukkitRunnable() {
            int ticksPassed = 0;
            final int maxTicks = durationSeconds * 20;

            @Override
            public void run() {
                if (!player.isOnline() || ticksPassed >= maxTicks) {
                    activeActionBarTasks.remove(uuid);
                    cancel();
                    return;
                }

                if (plugin.getActionBarFilter() != null) {
                    plugin.getActionBarFilter().markScoreActionBar(uuid);
                }
                player.sendActionBar(component);

                ticksPassed += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L); // Send every 10 ticks (0.5s) for smooth display without fade

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
