package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PearlManager {

    private final VCarterUtils plugin;

    private final Map<UUID, Integer> playerCooldownSettings = new ConcurrentHashMap<>();
    private final List<Integer> availableOptions = new ArrayList<>();
    private final Map<String, Integer> forcedWorldCooldowns = new ConcurrentHashMap<>();

    private boolean globalForceEnabled = false;
    private int globalForceSeconds = 1;

    private File dataFile;

    public PearlManager(VCarterUtils plugin) {
        this.plugin = plugin;
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
        playerCooldownSettings.clear();
        int minAllowed = getDefaultSeconds();
        synchronized (VCarterUtils.class) {
            FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            ConfigurationSection section = dataConfig.getConfigurationSection("pearl-cooldown");
            if (section == null) {
                return;
            }
            for (String key : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int seconds = section.getInt(key, minAllowed);
                    playerCooldownSettings.put(uuid, Math.max(minAllowed, seconds));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void savePlayerSettings() {
        if (dataFile == null) {
            return;
        }
        synchronized (VCarterUtils.class) {
            FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            for (Map.Entry<UUID, Integer> entry : playerCooldownSettings.entrySet()) {
                dataConfig.set("pearl-cooldown." + entry.getKey().toString(), entry.getValue());
            }
            try {
                dataConfig.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save playerdata.yml: " + e.getMessage());
            }
        }
    }

    public void savePlayerSettingAsync(UUID uuid, int seconds) {
        if (dataFile == null) {
            return;
        }
        final File fileToSave = this.dataFile;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (VCarterUtils.class) {
                FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(fileToSave);
                dataConfig.set("pearl-cooldown." + uuid.toString(), seconds);
                try {
                    dataConfig.save(fileToSave);
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not save playerdata.yml asynchronously: " + e.getMessage());
                }
            }
        });
    }

    public void reload() {
        int def = getDefaultSeconds();

        availableOptions.clear();
        List<Integer> rawOptions = plugin.getConfig().getIntegerList("pearl-cooldown.available-options");
        if (rawOptions.isEmpty()) {
            rawOptions = List.of(1, 3, 5, 6, 8, 10, 15);
        }

        // Chi cho phep cac moc >= default-seconds (khong bao gio cho chinh duoi muc mac dinh)
        for (int val : rawOptions) {
            if (val >= def && !availableOptions.contains(val)) {
                availableOptions.add(val);
            }
        }

        if (!availableOptions.contains(def)) {
            availableOptions.add(def);
        }
        Collections.sort(availableOptions);

        for (Map.Entry<UUID, Integer> entry : playerCooldownSettings.entrySet()) {
            int val = entry.getValue();
            if (val < def || !availableOptions.contains(val)) {
                entry.setValue(def);
            }
        }

        this.globalForceEnabled = plugin.getConfig().getBoolean("pearl-cooldown.global-force.enabled", false);
        this.globalForceSeconds = Math.max(def, plugin.getConfig().getInt("pearl-cooldown.global-force.seconds", def));

        forcedWorldCooldowns.clear();
        ConfigurationSection worldSection = plugin.getConfig().getConfigurationSection("pearl-cooldown.force-worlds.worlds");
        if (worldSection != null) {
            for (String worldName : worldSection.getKeys(false)) {
                int sec = worldSection.getInt(worldName, -1);
                if (sec >= def) {
                    forcedWorldCooldowns.put(worldName.trim().toLowerCase(), sec);
                }
            }
        }
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("pearl-cooldown.enabled", true);
    }

    public boolean isForceWorldsEnabled() {
        return plugin.getConfig().getBoolean("pearl-cooldown.force-worlds.enabled", true);
    }

    public boolean isGlobalForceEnabled() {
        return globalForceEnabled;
    }

    public int getGlobalForceSeconds() {
        return globalForceSeconds;
    }

    public void setGlobalForceEnabled(boolean enabled) {
        this.globalForceEnabled = enabled;
        plugin.getConfig().set("pearl-cooldown.global-force.enabled", enabled);
        plugin.saveConfig();
    }

    public void setGlobalForceSeconds(int seconds) {
        this.globalForceSeconds = Math.max(getDefaultSeconds(), seconds);
        plugin.getConfig().set("pearl-cooldown.global-force.seconds", this.globalForceSeconds);
        plugin.saveConfig();
    }

    public int cycleGlobalForceSeconds(boolean forward) {
        if (availableOptions.isEmpty()) {
            return globalForceSeconds;
        }
        int idx = availableOptions.indexOf(globalForceSeconds);
        int nextIdx;
        if (forward) {
            nextIdx = (idx == -1 || idx + 1 >= availableOptions.size()) ? 0 : idx + 1;
        } else {
            nextIdx = (idx <= 0) ? availableOptions.size() - 1 : idx - 1;
        }
        setGlobalForceSeconds(availableOptions.get(nextIdx));
        return globalForceSeconds;
    }

    public void setDefaultSeconds(int seconds) {
        int clean = Math.max(1, seconds);
        plugin.getConfig().set("pearl-cooldown.default-seconds", clean);
        plugin.saveConfig();
        reload();
    }

    public boolean isClearOnRespawn() {
        return plugin.getConfig().getBoolean("pearl-cooldown.clear-on-respawn", true);
    }

    public boolean isClearOnKitLoad() {
        return plugin.getConfig().getBoolean("pearl-cooldown.clear-on-kit-load", true);
    }

    public int getDefaultSeconds() {
        return Math.max(1, plugin.getConfig().getInt("pearl-cooldown.default-seconds", 1));
    }

    public List<Integer> getAvailableOptions() {
        return Collections.unmodifiableList(availableOptions);
    }

    public Set<String> getConfiguredForcedWorldNames() {
        return new HashSet<>(forcedWorldCooldowns.keySet());
    }

    public boolean isWorldForced(World world) {
        if (world == null || !isForceWorldsEnabled()) {
            return false;
        }
        return forcedWorldCooldowns.containsKey(world.getName().trim().toLowerCase());
    }

    public boolean isWorldForced(String worldName) {
        if (worldName == null || !isForceWorldsEnabled()) {
            return false;
        }
        return forcedWorldCooldowns.containsKey(worldName.trim().toLowerCase());
    }

    public int getForcedWorldSeconds(World world) {
        if (world == null) return -1;
        return getForcedWorldSeconds(world.getName());
    }

    public int getForcedWorldSeconds(String worldName) {
        if (!isWorldForced(worldName)) {
            return -1;
        }
        return forcedWorldCooldowns.getOrDefault(worldName.trim().toLowerCase(), -1);
    }

    public void setWorldForceSeconds(String worldName, int seconds) {
        if (worldName == null || worldName.trim().isEmpty()) {
            return;
        }
        String cleanKey = worldName.trim();
        String lowerKey = cleanKey.toLowerCase();

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("pearl-cooldown.force-worlds.worlds");
        if (sec != null) {
            for (String existingKey : new HashSet<>(sec.getKeys(false))) {
                if (existingKey.equalsIgnoreCase(cleanKey)) {
                    sec.set(existingKey, null);
                }
            }
        }

        if (seconds < getDefaultSeconds()) {
            forcedWorldCooldowns.remove(lowerKey);
        } else {
            forcedWorldCooldowns.put(lowerKey, seconds);
            plugin.getConfig().set("pearl-cooldown.force-worlds.enabled", true);
            plugin.getConfig().set("pearl-cooldown.force-worlds.worlds." + cleanKey, seconds);
        }
        plugin.saveConfig();
    }

    public int cycleWorldForceSeconds(String worldName) {
        int current = getForcedWorldSeconds(worldName);
        if (availableOptions.isEmpty()) {
            int next = (current < 0) ? getDefaultSeconds() : -1;
            setWorldForceSeconds(worldName, next);
            return next;
        }

        if (current < 0) {
            int first = availableOptions.get(0);
            setWorldForceSeconds(worldName, first);
            return first;
        }

        int idx = availableOptions.indexOf(current);
        if (idx == -1 || idx + 1 >= availableOptions.size()) {
            setWorldForceSeconds(worldName, -1);
            return -1;
        }

        int next = availableOptions.get(idx + 1);
        setWorldForceSeconds(worldName, next);
        return next;
    }

    public int getPlayerPersonalSeconds(UUID uuid) {
        int def = getDefaultSeconds();
        int val = playerCooldownSettings.getOrDefault(uuid, def);
        return Math.max(def, val);
    }

    public void setPlayerPersonalSeconds(UUID uuid, int seconds) {
        int clean = Math.max(getDefaultSeconds(), seconds);
        playerCooldownSettings.put(uuid, clean);
        savePlayerSettingAsync(uuid, clean);
    }

    public int cycleNextOption(UUID uuid) {
        if (availableOptions.isEmpty()) {
            return getDefaultSeconds();
        }
        int current = getPlayerPersonalSeconds(uuid);
        int idx = availableOptions.indexOf(current);
        int nextIdx = (idx == -1 || idx + 1 >= availableOptions.size()) ? 0 : idx + 1;
        int nextVal = availableOptions.get(nextIdx);
        setPlayerPersonalSeconds(uuid, nextVal);
        return nextVal;
    }

    public int cyclePreviousOption(UUID uuid) {
        if (availableOptions.isEmpty()) {
            return getDefaultSeconds();
        }
        int current = getPlayerPersonalSeconds(uuid);
        int idx = availableOptions.indexOf(current);
        int prevIdx = (idx <= 0) ? availableOptions.size() - 1 : idx - 1;
        int prevVal = availableOptions.get(prevIdx);
        setPlayerPersonalSeconds(uuid, prevVal);
        return prevVal;
    }

    public int resetToDefault(UUID uuid) {
        int def = getDefaultSeconds();
        setPlayerPersonalSeconds(uuid, def);
        return def;
    }

    public int getEffectiveCooldownSeconds(Player player) {
        if (player == null) {
            return getDefaultSeconds();
        }
        if (isWorldForced(player.getWorld())) {
            int forced = getForcedWorldSeconds(player.getWorld());
            if (forced >= getDefaultSeconds()) {
                return forced;
            }
        }
        if (globalForceEnabled) {
            return Math.max(getDefaultSeconds(), globalForceSeconds);
        }
        return getPlayerPersonalSeconds(player.getUniqueId());
    }

    public void clearCooldown(Player player) {
        if (player != null && player.isOnline()) {
            player.setCooldown(Material.ENDER_PEARL, 0);
        }
    }

    public void clearAll() {
        savePlayerSettings();
        forcedWorldCooldowns.clear();
        availableOptions.clear();
    }
}
