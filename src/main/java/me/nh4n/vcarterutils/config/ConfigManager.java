package me.nh4n.vcarterutils.config;

import me.nh4n.vcarterutils.VCarterUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Manages modular configuration files for VCarterUtils.
 * Splits features into dedicated .yml files while transparently syncing
 * with the Bukkit FileConfiguration for seamless backward compatibility.
 */
public class ConfigManager {

    private final VCarterUtils plugin;
    private final Map<String, FeatureConfig> featureConfigs = new LinkedHashMap<>();

    public record FeatureConfig(
            String name,
            String fileName,
            List<String> sectionKeys,
            File file,
            FileConfiguration configuration
    ) {}

    public ConfigManager(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers and loads all modular feature configuration files.
     */
    public void loadAll() {
        featureConfigs.clear();

        // 1. Proactive migration: If server has a legacy monolithic config.yml, split it cleanly
        migrateLegacyMonolithicConfig();

        // 2. Register all feature configuration files and their corresponding section keys
        registerFeature("config", "config.yml", List.of("admin", "theme"));
        registerFeature("regear", "regear.yml", List.of("regear"));
        registerFeature("training", "training.yml", List.of("training", "training-menu"));
        registerFeature("lobby", "lobby.yml", List.of("lobby-hotbar"));
        registerFeature("combattag", "combattag.yml", List.of("combat-tag"));
        registerFeature("totem", "totem.yml", List.of("totem", "autopot"));
        registerFeature("pearl", "pearl.yml", List.of("pearl-cooldown"));
        registerFeature("mace", "mace.yml", List.of("mace-cooldown"));
        registerFeature("xbow", "xbow.yml", List.of("xbow-counter"));
        registerFeature("back", "back.yml", List.of("back"));
        registerFeature("score", "score.yml", List.of("score"));
        registerFeature("menu", "menu.yml", List.of("setting-menu", "admin-menu"));

        // 3. Proactive migration: Ensure queue item in lobby.yml is IRON_SWORD and upgrade legacy typography/theme
        migrateLobbyQueueItem();
        migrateTypographyAndTheme();

        // 4. Merge all feature configurations into plugin.getConfig()
        syncToMainConfig();
    }

    private void registerFeature(String name, String fileName, List<String> sectionKeys) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (IllegalArgumentException e) {
                // If embedded resource does not exist, create empty file
                try {
                    if (plugin.getDataFolder().exists() || plugin.getDataFolder().mkdirs()) {
                        file.createNewFile();
                    }
                } catch (IOException ignored) {}
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Apply defaults from JAR resource if available
        InputStream defStream = plugin.getResource(fileName);
        if (defStream != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8)));
        }

        featureConfigs.put(name, new FeatureConfig(name, fileName, sectionKeys, file, config));
    }

    /**
     * Checks if a legacy monolithic config.yml exists and splits its sections into individual files,
     * then cleans up config.yml so that only admin settings remain.
     */
    private void migrateLegacyMonolithicConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            return;
        }

        YamlConfiguration mainYaml = YamlConfiguration.loadConfiguration(configFile);
        List<String> legacyKeys = List.of(
                "regear", "training", "training-menu", "lobby-hotbar",
                "combat-tag", "totem", "autopot", "pearl-cooldown",
                "mace-cooldown", "xbow-counter", "back", "score",
                "setting-menu", "admin-menu"
        );

        boolean hasLegacyKeys = false;
        for (String key : legacyKeys) {
            if (mainYaml.contains(key)) {
                hasLegacyKeys = true;
                break;
            }
        }

        if (!hasLegacyKeys) {
            return;
        }

        plugin.getLogger().info("Detected legacy monolithic config.yml. Backing up and modularizing into dedicated .yml files...");

        // Backup legacy config.yml
        File backupFile = new File(plugin.getDataFolder(), "config.yml.legacy-backup");
        if (!backupFile.exists()) {
            try {
                mainYaml.save(backupFile);
                plugin.getLogger().info("Backed up legacy config.yml to config.yml.legacy-backup");
            } catch (IOException ignored) {}
        }

        // Migrate sections to respective feature files if the feature file doesn't exist yet
        migrateSectionToFeatureFile(mainYaml, "regear.yml", List.of("regear"));
        migrateSectionToFeatureFile(mainYaml, "training.yml", List.of("training", "training-menu"));
        migrateSectionToFeatureFile(mainYaml, "lobby.yml", List.of("lobby-hotbar"));
        migrateSectionToFeatureFile(mainYaml, "combattag.yml", List.of("combat-tag"));
        migrateSectionToFeatureFile(mainYaml, "totem.yml", List.of("totem", "autopot"));
        migrateSectionToFeatureFile(mainYaml, "pearl.yml", List.of("pearl-cooldown"));
        migrateSectionToFeatureFile(mainYaml, "mace.yml", List.of("mace-cooldown"));
        migrateSectionToFeatureFile(mainYaml, "xbow.yml", List.of("xbow-counter"));
        migrateSectionToFeatureFile(mainYaml, "back.yml", List.of("back"));
        migrateSectionToFeatureFile(mainYaml, "score.yml", List.of("score"));
        migrateSectionToFeatureFile(mainYaml, "menu.yml", List.of("setting-menu", "admin-menu"));

        // Clean legacy sections from main config.yml so it remains minimal
        for (String key : legacyKeys) {
            mainYaml.set(key, null);
        }

        try {
            mainYaml.save(configFile);
            plugin.getLogger().info("Cleaned legacy sections from config.yml. Configurations are now fully modular.");
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save cleaned config.yml: " + e.getMessage());
        }
    }

    private void migrateSectionToFeatureFile(YamlConfiguration legacyYaml, String fileName, List<String> keys) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (Exception ignored) {}
        }

        if (file.exists()) {
            YamlConfiguration featConfig = YamlConfiguration.loadConfiguration(file);
            boolean modified = false;
            for (String key : keys) {
                if (legacyYaml.contains(key) && !featConfig.contains(key)) {
                    featConfig.set(key, legacyYaml.get(key));
                    modified = true;
                }
            }
            if (modified) {
                try {
                    featConfig.save(file);
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Fixes any outdated rtpq queue items in lobby.yml (e.g. nexo:join) to use IRON_SWORD.
     */
    private void migrateLobbyQueueItem() {
        FeatureConfig lobbyFc = featureConfigs.get("lobby");
        if (lobbyFc == null) return;
        FileConfiguration cfg = lobbyFc.configuration();
        boolean changed = false;

        String mat = cfg.getString("lobby-hotbar.items.rtpq.material", "");
        String nexoId = cfg.getString("lobby-hotbar.items.rtpq.nexo-id", "");
        String name = cfg.getString("lobby-hotbar.items.rtpq.name", "");

        if ("join".equalsIgnoreCase(nexoId) || "nexo:join".equalsIgnoreCase(mat) || (name != null && name.contains("<glyph:join>"))) {
            cfg.set("lobby-hotbar.items.rtpq.material", "IRON_SWORD");
            cfg.set("lobby-hotbar.items.rtpq.nexo-id", null);
            cfg.set("lobby-hotbar.items.rtpq.name", "<!italic><#a6e3a1><b>Matchmaking Queue</b> <#6c7086>▪ <#a6adc8>/rtpq");
            cfg.set("lobby-hotbar.items.rtpq.lore", List.of(
                    "<!italic><#6c7086>ᴄᴏᴍᴘᴇᴛɪᴛɪᴠᴇ ᴅᴜᴇʟs",
                    "",
                    "<!italic><#a6adc8>Join the 1v1 matchmaking queue",
                    "<!italic><#a6adc8>and fight other players.",
                    "",
                    "<!italic><white><glyph:right_click></white> <#a6e3a1>ᴊᴏɪɴ ǫᴜᴇᴜᴇ"
            ));
            changed = true;
        }

        // Synchronize cosmetics/cosmetic item (nexo:cosmetic -> Cosmetic)
        for (String key : List.of("decor", "cosmetics", "cosmetic")) {
            String cName = cfg.getString("lobby-hotbar.items." + key + ".name", "");
            if (cName != null && (cName.contains("<glyph:social>") || cName.contains("Decorations") || cName.contains("Cosmetics"))) {
                cfg.set("lobby-hotbar.items." + key + ".name", "<!italic><#f9e2af><b>Cosmetic</b> <#6c7086>▪ <#a6adc8>/decor");
                cfg.set("lobby-hotbar.items." + key + ".lore", List.of(
                        "<!italic><#6c7086>ᴠɪsᴜᴀʟ ᴄᴜsᴛᴏᴍɪᴢᴀᴛɪᴏɴ",
                        "",
                        "<!italic><#a6adc8>Customize your cosmetics,",
                        "<!italic><#a6adc8>outfits, and visual effects.",
                        "",
                        "<!italic><white><glyph:right_click></white> <#f9e2af>ᴏᴘᴇɴ sᴛᴜᴅɪᴏ"
                ));
                changed = true;
            }
        }

        // Synchronize settings item (nexo:settings -> Settings)
        String settingsName = cfg.getString("lobby-hotbar.items.settings.name", "");
        if (settingsName != null && settingsName.contains("<glyph:admin_setting>")) {
            cfg.set("lobby-hotbar.items.settings.name", "<!italic><#89dceb><b>Settings</b> <#6c7086>▪ <#a6adc8>/settings");
            changed = true;
        }

        if (changed) {
            try {
                cfg.save(lobbyFc.file());
                plugin.getLogger().info("Successfully synchronized lobby hotbar items in lobby.yml.");
            } catch (IOException ignored) {}
        }
    }

    /**
     * Automatically migrates legacy prefixes (<glyph:info_blue>, <glyph:warning_orange>, <glyph:staff_warning>,
     * <glyph:locked_red>, etc.) and outdated message/GUI formats to the clean warning_blue/red/purple typography,
     * while preserving all world lists, cooldown settings, and item whitelists.
     */
    private void migrateTypographyAndTheme() {
        List<String> legacyMarkers = List.of(
                "<glyph:info_blue>",
                "<glyph:warning_orange>",
                "<glyph:staff_warning>",
                "<glyph:locked_red>",
                "<glyph:player>",
                "<glyph:death>",
                "<glyph:kills>",
                "<glyph:ping>",
                "<glyph:info_icon>",
                "<glyph:admin_setting>",
                "<glyph:join>",
                "<glyph:cancel>",
                "<b>ENABLED</b>",
                "<b>DISABLED</b>",
                "<#6c7086>• "
        );

        Map<String, List<String>> uiPathsByFeature = Map.ofEntries(
                Map.entry("config", List.of("theme", "admin.messages")),
                Map.entry("training", List.of("training.messages", "training-menu")),
                Map.entry("lobby", List.of("lobby-hotbar.messages", "lobby-hotbar.items")),
                Map.entry("combattag", List.of("combat-tag.anti-interrupt.messages", "combat-tag.messages")),
                Map.entry("totem", List.of("totem.messages")),
                Map.entry("pearl", List.of("pearl-cooldown.messages")),
                Map.entry("mace", List.of("mace-cooldown.messages")),
                Map.entry("xbow", List.of("xbow-counter.action-bar-format", "xbow-counter.messages")),
                Map.entry("back", List.of("back.messages")),
                Map.entry("score", List.of("score.messages")),
                Map.entry("menu", List.of("setting-menu", "admin-menu"))
        );

        for (Map.Entry<String, List<String>> entry : uiPathsByFeature.entrySet()) {
            FeatureConfig fc = featureConfigs.get(entry.getKey());
            if (fc == null) continue;

            InputStream defStream = plugin.getResource(fc.fileName());
            if (defStream == null) continue;
            YamlConfiguration jarCfg = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            FileConfiguration diskCfg = fc.configuration();

            String dump = diskCfg.saveToString();
            boolean needsUpdate = false;
            for (String marker : legacyMarkers) {
                if (dump.contains(marker)) {
                    needsUpdate = true;
                    break;
                }
            }
            if ("config".equals(entry.getKey()) && !diskCfg.contains("theme.prefixes.purple")) {
                needsUpdate = true;
            }

            if (needsUpdate) {
                for (String path : entry.getValue()) {
                    if (jarCfg.contains(path)) {
                        diskCfg.set(path, jarCfg.get(path));
                    }
                }
                try {
                    diskCfg.save(fc.file());
                } catch (IOException ignored) {}
            }
        }

        // Ensure regear.yml uses its original <glyph:warning_blue> messages if modified
        FeatureConfig regearFc = featureConfigs.get("regear");
        if (regearFc != null) {
            FileConfiguration regearDisk = regearFc.configuration();
            String regearDump = regearDisk.saveToString();
            if (regearDump.contains("<sprite:items:item/shulker_shell>")
                    || regearDump.contains("%prefix_")
                    || regearDump.contains("ʀᴇsᴛᴏᴄᴋᴇᴅ ɪɴᴠᴇɴᴛᴏʀʏ")
                    || regearDump.contains("<glyph:info_blue>")) {
                InputStream regearStream = plugin.getResource("regear.yml");
                if (regearStream != null) {
                    YamlConfiguration regearJar = YamlConfiguration.loadConfiguration(new InputStreamReader(regearStream, StandardCharsets.UTF_8));
                    for (String path : List.of("regear.announcement.message", "regear.item.name", "regear.item.lore", "regear.messages")) {
                        if (regearJar.contains(path)) {
                            regearDisk.set(path, regearJar.get(path));
                        }
                    }
                    try {
                        regearDisk.save(regearFc.file());
                    } catch (IOException ignored) {}
                }
            }
        }
    }

    /**
     * Synchronizes all sections from individual feature files into plugin.getConfig().
     */
    public void syncToMainConfig() {
        FileConfiguration mainConfig = plugin.getConfig();

        for (FeatureConfig fc : featureConfigs.values()) {
            FileConfiguration cfg = fc.configuration();
            for (String key : fc.sectionKeys()) {
                if (cfg.contains(key)) {
                    mainConfig.set(key, null);
                    mainConfig.set(key, cfg.get(key));
                }
            }
        }
    }

    /**
     * Reloads all feature configuration files from disk and updates the main config.
     */
    public void reloadAll() {
        for (Map.Entry<String, FeatureConfig> entry : featureConfigs.entrySet()) {
            FeatureConfig fc = entry.getValue();
            try {
                ((YamlConfiguration) fc.configuration()).load(fc.file());
                InputStream defStream = plugin.getResource(fc.fileName());
                if (defStream != null) {
                    fc.configuration().setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8)));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to reload " + fc.fileName() + ": " + e.getMessage());
            }
        }

        migrateLobbyQueueItem();
        migrateTypographyAndTheme();
        syncToMainConfig();
    }

    /**
     * Saves changes from plugin.getConfig() back to their respective feature files.
     */
    public void saveAll() {
        FileConfiguration mainConfig = plugin.getConfig();

        for (FeatureConfig fc : featureConfigs.values()) {
            boolean changed = false;
            FileConfiguration cfg = fc.configuration();

            for (String key : fc.sectionKeys()) {
                if (mainConfig.contains(key)) {
                    cfg.set(key, mainConfig.get(key));
                    changed = true;
                }
            }

            if (changed || !fc.file().exists()) {
                try {
                    cfg.save(fc.file());
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not save " + fc.fileName() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Saves a specific feature configuration file.
     */
    public void saveFeature(String featureName) {
        FeatureConfig fc = featureConfigs.get(featureName);
        if (fc == null) return;

        FileConfiguration mainConfig = plugin.getConfig();
        FileConfiguration cfg = fc.configuration();

        for (String key : fc.sectionKeys()) {
            if (mainConfig.contains(key)) {
                cfg.set(key, mainConfig.get(key));
            }
        }

        try {
            cfg.save(fc.file());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save " + fc.fileName() + ": " + e.getMessage());
        }
    }

    public FileConfiguration getFeatureConfig(String featureName) {
        FeatureConfig fc = featureConfigs.get(featureName);
        return fc != null ? fc.configuration() : null;
    }

    public Map<String, FeatureConfig> getFeatureConfigs() {
        return Collections.unmodifiableMap(featureConfigs);
    }
}
