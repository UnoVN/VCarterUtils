package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class XbowManager {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    private final Map<UUID, Boolean> playerToggleSettings = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastXbowTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bestXbowTicks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> activeActionBarTasks = new ConcurrentHashMap<>();

    private File dataFile;

    public XbowManager(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        initDataFile();
        reload();
    }

    private void initDataFile() {
        dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!dataFile.exists()) {
            try {
                if (plugin.getDataFolder().exists() || plugin.getDataFolder().mkdirs()) {
                    dataFile.createNewFile();
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create playerdata.yml: " + e.getMessage());
            }
        }
        loadPlayerSettings();
    }

    private void loadPlayerSettings() {
        playerToggleSettings.clear();
        synchronized (VCarterUtils.class) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
            ConfigurationSection sec = cfg.getConfigurationSection("xbow-counter-enabled");
            if (sec == null) return;
            for (String key : sec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    playerToggleSettings.put(uuid, sec.getBoolean(key, true));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void savePlayerSettingsSync() {
        if (dataFile == null) return;
        synchronized (VCarterUtils.class) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
            for (Map.Entry<UUID, Boolean> entry : playerToggleSettings.entrySet()) {
                cfg.set("xbow-counter-enabled." + entry.getKey().toString(), entry.getValue());
            }
            try {
                cfg.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save playerdata.yml: " + e.getMessage());
            }
        }
    }

    private void savePlayerToggleAsync(UUID uuid, boolean enabled) {
        if (dataFile == null) return;
        final File fileToSave = this.dataFile;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (VCarterUtils.class) {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(fileToSave);
                cfg.set("xbow-counter-enabled." + uuid.toString(), enabled);
                try {
                    cfg.save(fileToSave);
                } catch (IOException ignored) {
                }
            }
        });
    }

    public void reload() {
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("xbow-counter.enabled", true);
    }

    public int getMaxSetupTicks() {
        return Math.max(20, plugin.getConfig().getInt("xbow-counter.max-setup-ticks", 200));
    }

    public int getDisplayDurationSeconds() {
        return Math.max(1, plugin.getConfig().getInt("xbow-counter.display-duration-seconds", 3));
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
        }

        if (!isPlayerEnabled(uuid)) {
            return;
        }

        String rawFormat = plugin.getConfig().getString(
                "xbow-counter.action-bar-format",
                "<#89dceb><b>Xbow Speed:</b> <#a6e3a1><b>%ticks% ticks</b> <#6c7086>(<#f9e2af>%seconds%s<#6c7086>)"
        );

        String secFormatted = String.format(Locale.US, "%.2f", cleanTicks / 20.0);
        Component actionBarComp = miniMessage.deserialize(
                rawFormat.replace("%ticks%", String.valueOf(cleanTicks))
                         .replace("%seconds%", secFormatted)
        );

        startActionBarDisplay(player, actionBarComp, getDisplayDurationSeconds());
    }

    private void startActionBarDisplay(Player player, Component component, int durationSeconds) {
        UUID uuid = player.getUniqueId();
        stopActionBarTask(uuid);

        int totalRuns = durationSeconds * 2;
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
        }.runTaskTimer(plugin, 0L, 10L);

        activeActionBarTasks.put(uuid, task);
    }

    public void stopActionBarTask(UUID uuid) {
        BukkitTask task = activeActionBarTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void clearAll() {
        savePlayerSettingsSync();
        for (BukkitTask task : activeActionBarTasks.values()) {
            task.cancel();
        }
        activeActionBarTasks.clear();
        lastXbowTicks.clear();
        bestXbowTicks.clear();
    }
}
