package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.config.PlayerDataManager;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class XbowManager {

    private final VCarterUtils plugin;
    private final PlayerDataManager playerData;

    private final Map<UUID, Boolean> playerToggleSettings = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastXbowTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bestXbowTicks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> activeActionBarTasks = new ConcurrentHashMap<>();

    private volatile boolean cachedEnabled = true;
    private volatile int cachedMaxSetupTicks = 200;
    private volatile int cachedDisplayDurationSeconds = 3;

    public XbowManager(VCarterUtils plugin) {
        this.plugin = plugin;
        this.playerData = plugin.getPlayerDataManager() != null
                ? plugin.getPlayerDataManager()
                : new PlayerDataManager(plugin);
        loadPlayerSettings();
        reload();
    }

    private void loadPlayerSettings() {
        playerToggleSettings.clear();
        bestXbowTicks.clear();
        playerData.read(cfg -> {
            ConfigurationSection sec = cfg.getConfigurationSection("xbow-counter-enabled");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        playerToggleSettings.put(uuid, sec.getBoolean(key, true));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            ConfigurationSection bestSec = cfg.getConfigurationSection("xbow-counter-best");
            if (bestSec != null) {
                for (String key : bestSec.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        int best = bestSec.getInt(key, 0);
                        if (best > 0) {
                            bestXbowTicks.put(uuid, best);
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        });
    }

    public void savePlayerSettingsSync() {
        playerData.updateSync(cfg -> {
            for (Map.Entry<UUID, Boolean> entry : playerToggleSettings.entrySet()) {
                cfg.set("xbow-counter-enabled." + entry.getKey().toString(), entry.getValue());
            }
            for (Map.Entry<UUID, Integer> entry : bestXbowTicks.entrySet()) {
                cfg.set("xbow-counter-best." + entry.getKey().toString(), entry.getValue());
            }
        });
        playerData.saveSyncIfDirty();
    }

    private void savePlayerToggleAsync(UUID uuid, boolean enabled) {
        playerData.updateAndSaveAsync(cfg ->
                cfg.set("xbow-counter-enabled." + uuid.toString(), enabled)
        );
    }

    private void savePlayerBestAsync(UUID uuid, int bestTicks) {
        playerData.updateAndSaveAsync(cfg ->
                cfg.set("xbow-counter-best." + uuid.toString(), bestTicks)
        );
    }

    public void reload() {
        this.cachedEnabled = plugin.getConfig().getBoolean("xbow-counter.enabled", true);
        this.cachedMaxSetupTicks = Math.max(20, plugin.getConfig().getInt("xbow-counter.max-setup-ticks", 200));
        this.cachedDisplayDurationSeconds = Math.max(1, plugin.getConfig().getInt("xbow-counter.display-duration-seconds", 3));
    }

    public boolean isEnabled() {
        return cachedEnabled;
    }

    public int getMaxSetupTicks() {
        return cachedMaxSetupTicks;
    }

    public int getDisplayDurationSeconds() {
        return cachedDisplayDurationSeconds;
    }

    public boolean isPlayerEnabled(UUID uuid) {
        return playerToggleSettings.getOrDefault(uuid, true);
    }

    public boolean togglePlayerSetting(UUID uuid) {
        boolean next = !isPlayerEnabled(uuid);
        playerToggleSettings.put(uuid, next);
        savePlayerToggleAsync(uuid, next);
        if (!next) {
            stopActionBarTask(uuid);
        }
        return next;
    }

    public int getLastTicks(UUID uuid) {
        return lastXbowTicks.getOrDefault(uuid, 0);
    }

    public int getBestTicks(UUID uuid) {
        return bestXbowTicks.getOrDefault(uuid, 0);
    }

    public void recordAndDisplayXbow(Player player, int ticks) {
        if (player == null || !player.isOnline() || !isEnabled()) return;

        UUID uuid = player.getUniqueId();
        int cleanTicks = Math.max(1, ticks);
        lastXbowTicks.put(uuid, cleanTicks);

        int currentBest = bestXbowTicks.getOrDefault(uuid, Integer.MAX_VALUE);
        if (cleanTicks < currentBest) {
            bestXbowTicks.put(uuid, cleanTicks);
            savePlayerBestAsync(uuid, cleanTicks);
        }

        if (!isPlayerEnabled(uuid)) {
            return;
        }

        String rawFormat = plugin.getConfig().getString(
                "xbow-counter.action-bar-format",
                "<white><sprite:items:item/crossbow_standby></white> <#89dceb><b>xʙᴏᴡ sᴘᴇᴇᴅ</b> <#6c7086>» <#a6e3a1><b>%ticks% ᴛɪᴄᴋs</b> <#6c7086>(<#f9e2af>%seconds%s<#6c7086>)"
        );

        String secFormatted = String.format(Locale.US, "%.2f", cleanTicks / 20.0);
        Component actionBarComp = plugin.parseMessage(
                rawFormat.replace("%ticks%", String.valueOf(cleanTicks))
                         .replace("%seconds%", secFormatted)
        );

        startActionBarDisplay(player, actionBarComp, getDisplayDurationSeconds());
    }

    private void startActionBarDisplay(Player player, Component component, int durationSeconds) {
        UUID uuid = player.getUniqueId();
        stopActionBarTask(uuid);

        player.sendActionBar(component);

        int totalRuns = Math.max(1, (durationSeconds * 2) - 1);
        BukkitTask task = new BukkitRunnable() {
            int runs = 0;

            @Override
            public void run() {
                if (!player.isOnline() || runs >= totalRuns) {
                    activeActionBarTasks.remove(uuid);
                    cancel();
                    return;
                }
                player.sendActionBar(component);
                runs++;
            }
        }.runTaskTimer(plugin, 10L, 10L);

        activeActionBarTasks.put(uuid, task);
    }

    public void stopActionBarTask(UUID uuid) {
        BukkitTask task = activeActionBarTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void clearAll() {
        playerData.saveSyncIfDirty();
        for (BukkitTask task : activeActionBarTasks.values()) {
            task.cancel();
        }
        activeActionBarTasks.clear();
        lastXbowTicks.clear();
        bestXbowTicks.clear();
    }
}
