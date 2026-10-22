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

    // Luu cooldown ca nhan cua nguoi choi (don vi: giay)
    private final Map<UUID, Integer> playerCooldownSettings = new ConcurrentHashMap<>();

    // Luu danh sach cac moc giay cho phep chon trong /setting
    private final List<Integer> availableOptions = new ArrayList<>();

    // Luu Force Cooldown theo tung world: Map<WorldNameLowerCase, Seconds>
    private final Map<String, Integer> forcedWorldCooldowns = new ConcurrentHashMap<>();

    private File dataFile;
    private FileConfiguration dataConfig;

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
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadPlayerSettings();
    }

    private void loadPlayerSettings() {
        playerCooldownSettings.clear();
        ConfigurationSection section = dataConfig.getConfigurationSection("pearl-cooldown");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int seconds = section.getInt(key, getDefaultSeconds());
                playerCooldownSettings.put(uuid, Math.max(0, seconds));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void savePlayerSettings() {
        if (dataConfig == null || dataFile == null) {
            return;
        }
        for (Map.Entry<UUID, Integer> entry : playerCooldownSettings.entrySet()) {
            dataConfig.set("pearl-cooldown." + entry.getKey().toString(), entry.getValue());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save playerdata.yml: " + e.getMessage());
        }
    }

    public void savePlayerSettingAsync(UUID uuid, int seconds) {
        if (dataConfig == null || dataFile == null) {
            return;
        }
        final File fileToSave = this.dataFile;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (this) {
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
        availableOptions.clear();
        List<Integer> rawOptions = plugin.getConfig().getIntegerList("pearl-cooldown.available-options");
        if (rawOptions.isEmpty()) {
            availableOptions.addAll(List.of(0, 1, 3, 5, 6, 8, 10, 15));
        } else {
            for (int val : rawOptions) {
                if (val >= 0 && !availableOptions.contains(val)) {
                    availableOptions.add(val);
                }
            }
            Collections.sort(availableOptions);
        }

        int def = getDefaultSeconds();
        if (!availableOptions.contains(def)) {
            availableOptions.add(def);
            Collections.sort(availableOptions);
        }

        forcedWorldCooldowns.clear();
        ConfigurationSection worldSection = plugin.getConfig().getConfigurationSection("pearl-cooldown.force-worlds.worlds");
        if (worldSection != null) {
            for (String worldName : worldSection.getKeys(false)) {
                int sec = worldSection.getInt(worldName, -1);
                if (sec >= 0) {
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

    public boolean isClearOnRespawn() {
        return plugin.getConfig().getBoolean("pearl-cooldown.clear-on-respawn", true);
    }

    public boolean isClearOnKitLoad() {
        return plugin.getConfig().getBoolean("pearl-cooldown.clear-on-kit-load", true);
    }

    public int getDefaultSeconds() {
        return Math.max(0, plugin.getConfig().getInt("pearl-cooldown.default-seconds", 3));
    }

    public List<Integer> getAvailableOptions() {
        return Collections.unmodifiableList(availableOptions);
    }

    /**
     * Kiem tra xem world hien tai co dang bi ep buoc (force) cooldown rieng hay khong
     */
    public boolean isWorldForced(World world) {
        if (world == null || !isForceWorldsEnabled()) {
            return false;
        }
        return forcedWorldCooldowns.containsKey(world.getName().trim().toLowerCase());
    }

    /**
     * Lay so giay Force Cooldown cua world (tra ve -1 neu world khong bi force)
     */
    public int getForcedWorldSeconds(World world) {
        if (!isWorldForced(world)) {
            return -1;
        }
        return forcedWorldCooldowns.getOrDefault(world.getName().trim().toLowerCase(), -1);
    }

    /**
     * Lay cooldown ca nhan ma nguoi choi da chon trong /setting
     */
    public int getPlayerPersonalSeconds(UUID uuid) {
        return playerCooldownSettings.getOrDefault(uuid, getDefaultSeconds());
    }

    /**
     * Dat cooldown ca nhan cho nguoi choi
     */
    public void setPlayerPersonalSeconds(UUID uuid, int seconds) {
        int clean = Math.max(0, seconds);
        playerCooldownSettings.put(uuid, clean);
        savePlayerSettingAsync(uuid, clean);
    }

    /**
     * Chuyen sang moc giay tiep theo trong danh sach available-options
     */
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

    /**
     * Chuyen ve moc giay truoc do trong danh sach available-options
     */
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

    /**
     * Reset cooldown ca nhan ve mac dinh
     */
    public int resetToDefault(UUID uuid) {
        int def = getDefaultSeconds();
        setPlayerPersonalSeconds(uuid, def);
        return def;
    }

    /**
     * Tinh toan so giay cooldown THUC TE ap dung cho nguoi choi tai world hien tai:
     * - Neu world hien tai nam trong force-worlds va force-worlds dang bat -> Ap dung cooldown cua world.
     * - Nguoc lai -> Ap dung cooldown ca nhan trong /setting cua nguoi choi.
     */
    public int getEffectiveCooldownSeconds(Player player) {
        if (player == null) {
            return getDefaultSeconds();
        }
        if (isWorldForced(player.getWorld())) {
            int forced = getForcedWorldSeconds(player.getWorld());
            if (forced >= 0) {
                return forced;
            }
        }
        return getPlayerPersonalSeconds(player.getUniqueId());
    }

    /**
     * Xoa ngay lap tuc hieu ung cooldown Ender Pearl tren nguoi choi
     */
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
