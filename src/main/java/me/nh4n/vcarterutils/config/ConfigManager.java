package me.nh4n.vcarterutils.config;

import me.nh4n.vcarterutils.VCarterUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Manages modular folder-organized configuration files for VCarterUtils:
 *   config.yml                  - Global theme & admin messages
 *   combat/                     - combattag.yml, mace.yml, pearl.yml, totem.yml
 *   gameplay/                   - regear.yml, score.yml, training.yml, xbow.yml
 *   utility/                    - back.yml, lobby.yml
 *   gui/                        - menu.yml
 *
 * Syncs all sections cleanly with plugin.getConfig() while keeping deleted keys
 * suppressed from JAR defaults and preserving untouched files/comments on save.
 */
public class ConfigManager {

    private final VCarterUtils plugin;
    private final File dataFolder;
    private final FileConfiguration mainConfig;
    private final Logger logger;
    private final Function<String, InputStream> resourceOpener;
    private final Map<String, FeatureConfig> featureConfigs = new LinkedHashMap<>();

    public record FeatureConfig(
            String name,
            String fileName,
            List<String> sectionKeys,
            File file,
            FileConfiguration configuration
    ) {}

    private record FeatureSpec(String name, String relativePath, List<String> sectionKeys) {
        String legacyFlatFileName() {
            int slash = relativePath.lastIndexOf('/');
            return slash >= 0 ? relativePath.substring(slash + 1) : relativePath;
        }
    }

    private static final List<FeatureSpec> FEATURE_SPECS = List.of(
            new FeatureSpec("config", "config.yml", List.of("admin", "theme")),
            new FeatureSpec("combattag", "combat/combattag.yml", List.of("combat-tag")),
            new FeatureSpec("mace", "combat/mace.yml", List.of("mace-cooldown")),
            new FeatureSpec("pearl", "combat/pearl.yml", List.of("pearl-cooldown")),
            new FeatureSpec("totem", "combat/totem.yml", List.of("totem", "autopot")),
            new FeatureSpec("regear", "gameplay/regear.yml", List.of("regear")),
            new FeatureSpec("score", "gameplay/score.yml", List.of("score")),
            new FeatureSpec("training", "gameplay/training.yml", List.of("training", "training-menu")),
            new FeatureSpec("xbow", "gameplay/xbow.yml", List.of("xbow-counter")),
            new FeatureSpec("back", "utility/back.yml", List.of("back")),
            new FeatureSpec("lobby", "utility/lobby.yml", List.of("lobby-hotbar")),
            new FeatureSpec("menu", "gui/menu.yml", List.of("setting-menu", "admin-menu"))
    );

    public ConfigManager(VCarterUtils plugin) {
        this(plugin, plugin.getDataFolder(), plugin.getConfig(), plugin.getLogger(), plugin::getResource);
    }

    /**
     * Test-friendly constructor allowing standalone verification without CraftBukkit.
     */
    public ConfigManager(
            VCarterUtils plugin,
            File dataFolder,
            FileConfiguration mainConfig,
            Logger logger,
            Function<String, InputStream> resourceOpener
    ) {
        this.plugin = plugin;
        this.dataFolder = dataFolder;
        this.mainConfig = mainConfig;
        this.logger = logger != null ? logger : Logger.getLogger("VCarterUtils");
        this.resourceOpener = resourceOpener != null ? resourceOpener : (path -> null);
    }

    /**
     * Registers, migrates, and loads all modular feature configuration files.
     */
    public void loadAll() {
        featureConfigs.clear();

        // 1. Move any root legacy backup into data/backups/ so root folder stays clean
        migrateLegacyBackups();

        // 2. If server has a legacy monolithic config.yml, split directly into subfolders
        migrateLegacyMonolithicConfig();

        // 3. Migrate pre-v1.3.0 flat root .yml files into their new feature folders
        migrateFlatFeatureFilesToFolders();

        // 4. Register & load all feature configuration files
        for (FeatureSpec spec : FEATURE_SPECS) {
            registerFeature(spec.name(), spec.relativePath(), spec.sectionKeys());
        }

        // 5. Proactive config upgrades
        migrateMissingTrainingMenuEntries();
        migrateLobbyQueueItem();
        migrateTypographyAndTheme();

        // 6. Sync loaded sections into mainConfig without leaking JAR defaults
        syncToMainConfig();
    }

    private void registerFeature(String name, String relativePath, List<String> sectionKeys) {
        File file = new File(dataFolder, relativePath);
        if (!file.exists()) {
            extractOrCreateFile(relativePath, file);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        // Never attach JAR defaults onto live configs so deleted keys (e.g. ffa_diamond) stay deleted
        config.setDefaults(new YamlConfiguration());

        featureConfigs.put(name, new FeatureConfig(name, relativePath, sectionKeys, file, config));
    }

    private void extractOrCreateFile(String relativePath, File targetFile) {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (plugin != null) {
            try {
                plugin.saveResource(relativePath, false);
                if (targetFile.exists()) {
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // Fall back to stream copy or empty file
            }
        }

        try (InputStream stream = resourceOpener.apply(relativePath)) {
            if (stream != null) {
                Files.copy(stream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        } catch (IOException e) {
            logger.warning("Could not extract default resource " + relativePath + ": " + e.getMessage());
        }

        try {
            targetFile.createNewFile();
        } catch (IOException ignored) {
        }
    }

    private void migrateLegacyBackups() {
        File oldBackup = new File(dataFolder, "config.yml.legacy-backup");
        if (oldBackup.exists() && oldBackup.isFile()) {
            File targetBackup = new File(dataFolder, "data/backups/config.yml.legacy-backup");
            if (!targetBackup.exists()) {
                File parent = targetBackup.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try {
                    Files.move(oldBackup.toPath(), targetBackup.toPath());
                    logger.info("Moved config.yml.legacy-backup -> data/backups/config.yml.legacy-backup");
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Checks if a legacy monolithic config.yml exists and splits its sections into individual
     * subfolder feature files, then cleans up config.yml so only theme & admin remain.
     */
    private void migrateLegacyMonolithicConfig() {
        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            return;
        }

        YamlConfiguration mainYaml = YamlConfiguration.loadConfiguration(configFile);
        List<String> legacyKeys = new ArrayList<>();
        for (FeatureSpec spec : FEATURE_SPECS) {
            if (!"config".equals(spec.name())) {
                legacyKeys.addAll(spec.sectionKeys());
            }
        }

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

        logger.info("Detected legacy monolithic config.yml. Backing up and modularizing into feature folders...");

        boolean replaceConfigTemplate = !mainYaml.contains("theme");
        File backupFile = new File(dataFolder, "data/backups/config.yml.legacy-backup");
        if (!backupFile.exists()) {
            File backupParent = backupFile.getParentFile();
            if (backupParent != null && !backupParent.exists()) {
                backupParent.mkdirs();
            }
            try {
                mainYaml.save(backupFile);
                logger.info("Backed up legacy config.yml to data/backups/config.yml.legacy-backup");
            } catch (IOException ignored) {
            }
        }

        for (FeatureSpec spec : FEATURE_SPECS) {
            if (!"config".equals(spec.name())) {
                migrateSectionToFeatureFile(mainYaml, spec.relativePath(), spec.sectionKeys());
            }
        }

        for (String key : legacyKeys) {
            mainYaml.set(key, null);
        }

        try {
            if (replaceConfigTemplate) {
                try (InputStream stream = resourceOpener.apply("config.yml")) {
                    if (stream != null) {
                        Files.copy(stream, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        YamlConfiguration freshConfig = YamlConfiguration.loadConfiguration(configFile);
                        if (mainYaml.isConfigurationSection("admin")
                                && mergeConfigPath(mainYaml, freshConfig, "admin", "admin")) {
                            freshConfig.save(configFile);
                        }
                    } else {
                        mainYaml.save(configFile);
                    }
                }
            } else {
                mainYaml.save(configFile);
            }
            logger.info("Cleaned legacy sections from config.yml. Configurations are now organized in feature folders.");
        } catch (IOException e) {
            logger.warning("Could not save cleaned config.yml: " + e.getMessage());
        }
    }

    /**
     * Moves pre-v1.3.0 flat feature files at dataFolder root (e.g. pearl.yml) into their new
     * feature folders (e.g. combat/pearl.yml) without overwriting existing folder files.
     */
    private void migrateFlatFeatureFilesToFolders() {
        for (FeatureSpec spec : FEATURE_SPECS) {
            if (!spec.relativePath().contains("/")) {
                continue;
            }
            File legacyFlat = new File(dataFolder, spec.legacyFlatFileName());
            if (!legacyFlat.exists() || !legacyFlat.isFile()) {
                continue;
            }
            File target = new File(dataFolder, spec.relativePath());
            if (!target.exists()) {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try {
                    Files.move(legacyFlat.toPath(), target.toPath());
                    logger.info("Moved " + spec.legacyFlatFileName() + " -> " + spec.relativePath());
                } catch (IOException e) {
                    logger.warning("Could not move " + spec.legacyFlatFileName() + " to " + spec.relativePath() + ": " + e.getMessage());
                }
            } else {
                logger.warning("Found both legacy " + spec.legacyFlatFileName() + " and " + spec.relativePath()
                        + "; keeping " + spec.relativePath() + ".");
            }
        }
    }

    private static final Set<String> AUTHORITATIVE_LEGACY_SECTIONS = Set.of(
            "pearl-cooldown.force-worlds.worlds",
            "mace-cooldown.force-worlds.worlds",
            "combat-tag.force-worlds.worlds",
            "combat-tag.blocked-commands.worlds",
            "combat-tag.anti-interrupt.worlds",
            "lobby-hotbar.items",
            "setting-menu.items"
    );

    private void migrateSectionToFeatureFile(YamlConfiguration legacyYaml, String relativePath, List<String> keys) {
        File file = new File(dataFolder, relativePath);
        boolean newlyExtracted = !file.exists();
        if (newlyExtracted) {
            extractOrCreateFile(relativePath, file);
        }

        if (file.exists()) {
            YamlConfiguration featConfig = YamlConfiguration.loadConfiguration(file);
            boolean modified = false;
            for (String key : keys) {
                if (legacyYaml.isSet(key)) {
                    if (newlyExtracted) {
                        if (mergeConfigPath(legacyYaml, featConfig, key, key)) {
                            modified = true;
                        }
                    } else if (!featConfig.isSet(key)) {
                        copyConfigPath(legacyYaml, featConfig, key);
                        modified = true;
                    }
                }
            }
            if (modified) {
                try {
                    featConfig.save(file);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private boolean mergeConfigPath(ConfigurationSection source, ConfigurationSection target, String path, String fullPath) {
        if (source.isConfigurationSection(path)) {
            if (AUTHORITATIVE_LEGACY_SECTIONS.contains(fullPath)) {
                return syncDiffToTarget(source, target, path);
            }
            ConfigurationSection srcSec = source.getConfigurationSection(path);
            ConfigurationSection dstSec = target.getConfigurationSection(path);
            boolean modified = false;
            if (dstSec == null) {
                dstSec = target.createSection(path);
                modified = true;
            }
            if (srcSec != null) {
                for (String child : srcSec.getKeys(false)) {
                    String childFull = fullPath.isEmpty() ? child : fullPath + "." + child;
                    if (mergeConfigPath(srcSec, dstSec, child, childFull)) {
                        modified = true;
                    }
                }
            }
            return modified;
        }
        Object srcVal = source.get(path);
        if (target.isConfigurationSection(path) || !Objects.equals(srcVal, target.get(path))) {
            target.set(path, cloneYamlValue(srcVal));
            return true;
        }
        return false;
    }

    /**
     * One-time upgrade for older gui/menu.yml files missing the Training Mode button
     * or colliding at slot 13 after monolithic migration.
     * Never overrides an explicitly disabled or custom non-colliding training-mode entry.
     */
    private void migrateMissingTrainingMenuEntries() {
        FeatureConfig menuFc = featureConfigs.get("menu");
        if (menuFc == null) {
            return;
        }
        FileConfiguration cfg = menuFc.configuration();
        if (!cfg.isConfigurationSection("setting-menu.items")) {
            return;
        }

        boolean hasTraining = cfg.isSet("setting-menu.items.training-mode");
        boolean trainingEnabled = cfg.getBoolean("setting-menu.items.training-mode.enabled", true);
        int trainingSlot = cfg.getInt("setting-menu.items.training-mode.slot", 13);
        int xbowSlot = cfg.getInt("setting-menu.items.xbow-counter-toggle.slot", -1);
        int lobbySlot = cfg.getInt("setting-menu.items.lobby-hotbar-toggle.slot", -1);

        boolean changed = false;
        if (!hasTraining || (trainingEnabled && trainingSlot == 13 && xbowSlot == 13)) {
            if (xbowSlot == 13) {
                cfg.set("setting-menu.items.xbow-counter-toggle.slot", 14);
                changed = true;
                if (lobbySlot == 14) {
                    cfg.set("setting-menu.items.lobby-hotbar-toggle.slot", 15);
                }
            }
        }

        if (!hasTraining) {
            try (InputStream defStream = resourceOpener.apply(menuFc.fileName())) {
                if (defStream != null) {
                    YamlConfiguration jarCfg = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
                    if (jarCfg.isSet("setting-menu.items.training-mode")) {
                        copyConfigPath(jarCfg, cfg, "setting-menu.items.training-mode");
                        changed = true;
                    }
                }
            } catch (IOException ignored) {
            }
        }

        if (changed) {
            try {
                cfg.save(menuFc.file());
                logger.info("Migrated Training Mode button into " + menuFc.fileName());
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Fixes any outdated rtpq/cosmetic/settings default items in utility/lobby.yml
     * without overwriting modern user customizations.
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

        for (String key : List.of("decor", "cosmetics", "cosmetic")) {
            String cName = cfg.getString("lobby-hotbar.items." + key + ".name", "");
            if (cName != null && (cName.contains("<glyph:social>")
                    || cName.contains("<b>Decorations</b>")
                    || cName.contains("<b>Cosmetics</b> <#6c7086>(/decor)"))) {
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

        String settingsName = cfg.getString("lobby-hotbar.items.settings.name", "");
        if (settingsName != null && settingsName.contains("<glyph:admin_setting>")) {
            cfg.set("lobby-hotbar.items.settings.name", "<!italic><#89dceb><b>Settings</b> <#6c7086>▪ <#a6adc8>/settings");
            changed = true;
        }

        if (changed) {
            try {
                cfg.save(lobbyFc.file());
                logger.info("Successfully synchronized lobby hotbar items in " + lobbyFc.fileName());
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Migrates only individual leaf entries containing genuinely obsolete legacy glyphs/markers
     * to the clean warning_blue/red/purple typography without clobbering customized configs.
     */
    private void migrateTypographyAndTheme() {
        List<String> legacyGlyphMarkers = List.of(
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
                "<glyph:cancel>"
        );
        List<String> legacyStatusMarkers = List.of("<b>ENABLED</b>", "<b>DISABLED</b>");
        Set<String> statusLeafPaths = Set.of(
                "lobby-hotbar.messages.status-enabled",
                "lobby-hotbar.messages.status-disabled",
                "xbow-counter.messages.status-enabled",
                "xbow-counter.messages.status-disabled",
                "training.messages.status-enabled",
                "training.messages.status-disabled"
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

            try (InputStream defStream = resourceOpener.apply(fc.fileName())) {
                if (defStream == null) continue;
                YamlConfiguration jarCfg = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
                FileConfiguration diskCfg = fc.configuration();

                boolean changed = false;
                for (String rootPath : entry.getValue()) {
                    if (!jarCfg.isSet(rootPath)) continue;
                    for (String leafPath : collectLeafPaths(jarCfg, rootPath)) {
                        if (!diskCfg.isSet(leafPath)) continue;
                        Object diskVal = diskCfg.get(leafPath);
                        boolean upgradeLeaf = containsAnyMarker(diskVal, legacyGlyphMarkers)
                                || (statusLeafPaths.contains(leafPath) && containsAnyMarker(diskVal, legacyStatusMarkers));
                        if (upgradeLeaf) {
                            diskCfg.set(leafPath, cloneYamlValue(jarCfg.get(leafPath)));
                            changed = true;
                        }
                    }
                }

                if ("config".equals(entry.getKey())) {
                    // Fill only missing theme keys individually so admin theme overrides stay intact
                    for (String themeSection : List.of("theme.prefixes", "theme.colors", "admin.messages")) {
                        ConfigurationSection sec = jarCfg.getConfigurationSection(themeSection);
                        if (sec != null) {
                            for (String key : sec.getKeys(false)) {
                                String fullPath = themeSection + "." + key;
                                if (!diskCfg.isSet(fullPath)) {
                                    diskCfg.set(fullPath, cloneYamlValue(sec.get(key)));
                                    changed = true;
                                }
                            }
                        }
                    }
                }

                if (changed) {
                    try {
                        diskCfg.save(fc.file());
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        }

        // Upgrade only genuinely obsolete regear entries while respecting custom %prefix_*% overrides
        FeatureConfig regearFc = featureConfigs.get("regear");
        if (regearFc != null) {
            FileConfiguration regearDisk = regearFc.configuration();
            List<String> legacyRegearMarkers = List.of(
                    "<sprite:items:item/shulker_shell>",
                    "ʀᴇsᴛᴏᴄᴋᴇᴅ ɪɴᴠᴇɴᴛᴏʀʏ",
                    "<glyph:info_blue>"
            );
            try (InputStream regearStream = resourceOpener.apply(regearFc.fileName())) {
                if (regearStream != null) {
                    YamlConfiguration regearJar = YamlConfiguration.loadConfiguration(new InputStreamReader(regearStream, StandardCharsets.UTF_8));
                    boolean regearChanged = false;
                    for (String rootPath : List.of("regear.announcement.message", "regear.item.name", "regear.item.lore", "regear.messages")) {
                        if (!regearJar.isSet(rootPath)) continue;
                        for (String leafPath : collectLeafPaths(regearJar, rootPath)) {
                            if (regearDisk.isSet(leafPath) && containsAnyMarker(regearDisk.get(leafPath), legacyRegearMarkers)) {
                                regearDisk.set(leafPath, cloneYamlValue(regearJar.get(leafPath)));
                                regearChanged = true;
                            }
                        }
                    }
                    if (regearChanged) {
                        try {
                            regearDisk.save(regearFc.file());
                        } catch (IOException ignored) {
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    private List<String> collectLeafPaths(ConfigurationSection root, String rootPath) {
        List<String> leaves = new ArrayList<>();
        if (root.isConfigurationSection(rootPath)) {
            ConfigurationSection sec = root.getConfigurationSection(rootPath);
            if (sec != null) {
                for (String key : sec.getKeys(true)) {
                    if (!sec.isConfigurationSection(key)) {
                        leaves.add(rootPath + "." + key);
                    }
                }
            }
        } else if (root.isSet(rootPath)) {
            leaves.add(rootPath);
        }
        return leaves;
    }

    private boolean containsAnyMarker(Object value, List<String> markers) {
        if (value instanceof String s) {
            for (String marker : markers) {
                if (s.contains(marker)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof List<?> list) {
            for (Object elem : list) {
                if (containsAnyMarker(elem, markers)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Synchronizes all sections from individual feature files into mainConfig cleanly.
     */
    public void syncToMainConfig() {
        for (FeatureConfig fc : featureConfigs.values()) {
            FileConfiguration cfg = fc.configuration();
            for (String key : fc.sectionKeys()) {
                mainConfig.set(key, null);
                if (cfg.isSet(key)) {
                    copyConfigPath(cfg, mainConfig, key);
                }
            }
        }
    }

    /**
     * Reloads all feature configuration files from disk and updates the main config.
     */
    public void reloadAll() {
        migrateLegacyBackups();
        migrateLegacyMonolithicConfig();
        migrateFlatFeatureFilesToFolders();

        for (FeatureConfig fc : featureConfigs.values()) {
            try {
                if (!fc.file().exists()) {
                    extractOrCreateFile(fc.fileName(), fc.file());
                }
                YamlConfiguration yamlCfg = (YamlConfiguration) fc.configuration();
                yamlCfg.load(fc.file());
                yamlCfg.setDefaults(new YamlConfiguration());
            } catch (Exception e) {
                logger.warning("Failed to reload " + fc.fileName() + ": " + e.getMessage());
            }
        }

        migrateMissingTrainingMenuEntries();
        migrateLobbyQueueItem();
        migrateTypographyAndTheme();
        syncToMainConfig();
    }

    /**
     * Saves only modified feature configuration files back to disk.
     */
    public void saveAll() {
        for (FeatureConfig fc : featureConfigs.values()) {
            saveFeatureInternal(fc);
        }
    }

    /**
     * Saves a specific feature configuration file if its keys changed or file is missing.
     */
    public void saveFeature(String featureName) {
        FeatureConfig fc = featureConfigs.get(featureName);
        if (fc == null) return;
        saveFeatureInternal(fc);
    }

    private void saveFeatureInternal(FeatureConfig fc) {
        FileConfiguration cfg = fc.configuration();
        boolean changed = false;

        for (String key : fc.sectionKeys()) {
            if (syncDiffToTarget(mainConfig, cfg, key)) {
                changed = true;
            }
        }

        if (changed || !fc.file().exists()) {
            try {
                File parent = fc.file().getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                cfg.save(fc.file());
            } catch (IOException e) {
                logger.warning("Could not save " + fc.fileName() + ": " + e.getMessage());
            }
        }
    }

    private void copyConfigPath(ConfigurationSection source, ConfigurationSection target, String path) {
        if (source.isConfigurationSection(path)) {
            ConfigurationSection srcSec = source.getConfigurationSection(path);
            ConfigurationSection dstSec = target.createSection(path);
            if (srcSec != null) {
                for (String child : srcSec.getKeys(false)) {
                    copyConfigPath(srcSec, dstSec, child);
                }
            }
        } else {
            target.set(path, cloneYamlValue(source.get(path)));
        }
    }

    /**
     * Recursively diffs source against target at path and mutates only changed leaves/keys,
     * preserving YAML comments on untouched sibling keys in target.
     */
    private boolean syncDiffToTarget(ConfigurationSection source, ConfigurationSection target, String path) {
        if (!source.isSet(path)) {
            if (target.isSet(path)) {
                target.set(path, null);
                return true;
            }
            return false;
        }

        if (source.isConfigurationSection(path)) {
            ConfigurationSection srcSec = source.getConfigurationSection(path);
            boolean changed = false;
            ConfigurationSection dstSec = target.getConfigurationSection(path);
            if (dstSec == null) {
                dstSec = target.createSection(path);
                changed = true;
            }

            if (srcSec != null) {
                Set<String> srcKeys = srcSec.getKeys(false);
                for (String existing : new HashSet<>(dstSec.getKeys(false))) {
                    if (!srcKeys.contains(existing)) {
                        dstSec.set(existing, null);
                        changed = true;
                    }
                }
                for (String key : srcKeys) {
                    if (syncDiffToTarget(srcSec, dstSec, key)) {
                        changed = true;
                    }
                }
            }
            return changed;
        }

        Object srcVal = source.get(path);
        if (target.isConfigurationSection(path) || !Objects.equals(srcVal, target.get(path))) {
            target.set(path, cloneYamlValue(srcVal));
            return true;
        }
        return false;
    }

    private Object cloneYamlValue(Object val) {
        if (val instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(cloneYamlValue(item));
            }
            return copy;
        }
        if (val instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(entry.getKey(), cloneYamlValue(entry.getValue()));
            }
            return copy;
        }
        return val;
    }

    public FileConfiguration getFeatureConfig(String featureName) {
        FeatureConfig fc = featureConfigs.get(featureName);
        return fc != null ? fc.configuration() : null;
    }

    public Map<String, FeatureConfig> getFeatureConfigs() {
        return Collections.unmodifiableMap(featureConfigs);
    }
}
