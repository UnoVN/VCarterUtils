package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

public class MaceManager {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    // Luu cooldown Mace ca nhan cua nguoi choi (don vi: giay)
    private final Map<UUID, Integer> playerCooldownSettings = new ConcurrentHashMap<>();

    // Luu danh sach cac moc giay cho phep chon
    private final List<Integer> availableOptions = new ArrayList<>();

    // Luu Force Cooldown theo tung world: Map<WorldNameLowerCase, Seconds>
    private final Map<String, Integer> forcedWorldCooldowns = new ConcurrentHashMap<>();

    // Chong spam tin nhan khi nguoi choi click danh lien tuc luc Mace dang cooldown
    private final Map<UUID, Long> lastCooldownWarnTimes = new ConcurrentHashMap<>();

    private boolean globalForceEnabled = false;
    private int globalForceSeconds = 5;

    private File dataFile;

    public MaceManager(VCarterUtils plugin) {
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
        playerCooldownSettings.clear();
        synchronized (VCarterUtils.class) {
            FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            ConfigurationSection section = dataConfig.getConfigurationSection("mace-cooldown");
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
    }

    public void savePlayerSettings() {
        if (dataFile == null) {
            return;
        }
        synchronized (VCarterUtils.class) {
            FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            for (Map.Entry<UUID, Integer> entry : playerCooldownSettings.entrySet()) {
                dataConfig.set("mace-cooldown." + entry.getKey().toString(), entry.getValue());
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
                dataConfig.set("mace-cooldown." + uuid.toString(), seconds);
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
        List<Integer> rawOptions = plugin.getConfig().getIntegerList("mace-cooldown.available-options");
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

        this.globalForceEnabled = plugin.getConfig().getBoolean("mace-cooldown.global-force.enabled", false);
        this.globalForceSeconds = Math.max(0, plugin.getConfig().getInt("mace-cooldown.global-force.seconds", def));

        forcedWorldCooldowns.clear();
        ConfigurationSection worldSection = plugin.getConfig().getConfigurationSection("mace-cooldown.force-worlds.worlds");
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
        return plugin.getConfig().getBoolean("mace-cooldown.enabled", true);
    }

    public boolean isForceWorldsEnabled() {
        return plugin.getConfig().getBoolean("mace-cooldown.force-worlds.enabled", true);
    }

    public boolean isGlobalForceEnabled() {
        return globalForceEnabled;
    }

    public int getGlobalForceSeconds() {
        return globalForceSeconds;
    }

    public void setGlobalForceEnabled(boolean enabled) {
        this.globalForceEnabled = enabled;
        plugin.getConfig().set("mace-cooldown.global-force.enabled", enabled);
        plugin.saveConfig();
    }

    public void setGlobalForceSeconds(int seconds) {
        this.globalForceSeconds = Math.max(0, seconds);
        plugin.getConfig().set("mace-cooldown.global-force.seconds", this.globalForceSeconds);
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
        int clean = Math.max(0, seconds);
        plugin.getConfig().set("mace-cooldown.default-seconds", clean);
        plugin.saveConfig();
    }

    public boolean isOnlyPlayers() {
        return plugin.getConfig().getBoolean("mace-cooldown.only-players", false);
    }

    public boolean isOnlySmashAttack() {
        return plugin.getConfig().getBoolean("mace-cooldown.only-smash-attack", false);
    }

    public boolean isCancelAttackOnCooldown() {
        return plugin.getConfig().getBoolean("mace-cooldown.cancel-attack-on-cooldown", true);
    }

    public boolean isClearOnRespawn() {
        return plugin.getConfig().getBoolean("mace-cooldown.clear-on-respawn", true);
    }

    public boolean isClearOnKitLoad() {
        return plugin.getConfig().getBoolean("mace-cooldown.clear-on-kit-load", true);
    }

    public int getDefaultSeconds() {
        return Math.max(0, plugin.getConfig().getInt("mace-cooldown.default-seconds", 5));
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

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("mace-cooldown.force-worlds.worlds");
        if (sec != null) {
            for (String existingKey : new HashSet<>(sec.getKeys(false))) {
                if (existingKey.equalsIgnoreCase(cleanKey)) {
                    sec.set(existingKey, null);
                }
            }
        }

        if (seconds < 0) {
            forcedWorldCooldowns.remove(lowerKey);
        } else {
            forcedWorldCooldowns.put(lowerKey, seconds);
            plugin.getConfig().set("mace-cooldown.force-worlds.enabled", true);
            plugin.getConfig().set("mace-cooldown.force-worlds.worlds." + cleanKey, seconds);
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
        return playerCooldownSettings.getOrDefault(uuid, getDefaultSeconds());
    }

    public void setPlayerPersonalSeconds(UUID uuid, int seconds) {
        int clean = Math.max(0, seconds);
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
            if (forced >= 0) {
                return forced;
            }
        }
        if (globalForceEnabled) {
            return globalForceSeconds;
        }
        return getPlayerPersonalSeconds(player.getUniqueId());
    }

    public void sendOnCooldownWarning(Player player, int remainingTicks) {
        long now = System.currentTimeMillis();
        Long lastWarn = lastCooldownWarnTimes.get(player.getUniqueId());
        if (lastWarn != null && (now - lastWarn < 1500L)) {
            return;
        }
        lastCooldownWarnTimes.put(player.getUniqueId(), now);

        String raw = plugin.getConfig().getString(
                "mace-cooldown.messages.on-cooldown",
                "<glyph:warning_blue> <#f38ba8>Your <#89dceb>Mace<#f38ba8> is on cooldown! Wait <#f9e2af><b>%seconds%s</b><#f38ba8>."
        );
        if (raw != null && !raw.isEmpty()) {
            String secFormatted = String.format(Locale.US, "%.1f", remainingTicks / 20.0);
            player.sendMessage(miniMessage.deserialize(raw.replace("%seconds%", secFormatted)));
        }
    }

    public void clearCooldown(Player player) {
        if (player != null && player.isOnline()) {
            player.setCooldown(Material.MACE, 0);
        }
    }

    public void clearAll() {
        savePlayerSettings();
        forcedWorldCooldowns.clear();
        availableOptions.clear();
        lastCooldownWarnTimes.clear();
    }
}
