package me.nh4n.vcarterutils.config;

import me.nh4n.vcarterutils.VCarterUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Centralizes thread-safe persistence for data/playerdata.yml across all managers.
 * Eliminates duplicate YAML parsing/saving and prevents concurrent async write races.
 */
public class PlayerDataManager {

    public static final String RELATIVE_PATH = "data/playerdata.yml";

    private final VCarterUtils plugin;
    private final File dataFolder;
    private final Logger logger;
    private final File dataFile;
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);

    private FileConfiguration dataConfig;
    private boolean dirty = false;

    public PlayerDataManager(VCarterUtils plugin) {
        this(plugin, plugin.getDataFolder(), plugin.getLogger());
    }

    public PlayerDataManager(VCarterUtils plugin, File dataFolder, Logger logger) {
        this.plugin = plugin;
        this.dataFolder = dataFolder;
        this.logger = logger != null ? logger : Logger.getLogger("VCarterUtils");
        this.dataFile = new File(dataFolder, RELATIVE_PATH);
        init();
    }

    private void init() {
        synchronized (VCarterUtils.class) {
            ensureParentDir();
            migrateLegacyRootPlayerData();

            if (!dataFile.exists()) {
                try {
                    dataFile.createNewFile();
                } catch (IOException e) {
                    logger.warning("Could not create " + RELATIVE_PATH + ": " + e.getMessage());
                }
            }
            this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            this.dirty = false;
        }
    }

    private void migrateLegacyRootPlayerData() {
        File legacyFile = new File(dataFolder, "playerdata.yml");
        if (legacyFile.exists() && legacyFile.isFile()) {
            if (!dataFile.exists()) {
                try {
                    Files.move(legacyFile.toPath(), dataFile.toPath());
                    logger.info("Migrated playerdata.yml -> " + RELATIVE_PATH);
                } catch (IOException e) {
                    logger.warning("Could not move playerdata.yml to " + RELATIVE_PATH + ": " + e.getMessage());
                }
            } else {
                logger.warning("Found both legacy playerdata.yml and " + RELATIVE_PATH + "; using " + RELATIVE_PATH + ".");
            }
        }
    }

    private void ensureParentDir() {
        File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    public File getDataFile() {
        return dataFile;
    }

    public void read(Consumer<FileConfiguration> reader) {
        if (reader == null) return;
        synchronized (VCarterUtils.class) {
            reader.accept(dataConfig);
        }
    }

    public void updateSync(Consumer<FileConfiguration> mutator) {
        if (mutator == null) return;
        synchronized (VCarterUtils.class) {
            mutator.accept(dataConfig);
            dirty = true;
        }
    }

    public void updateAndSaveAsync(Consumer<FileConfiguration> mutator) {
        if (mutator == null) return;
        synchronized (VCarterUtils.class) {
            mutator.accept(dataConfig);
            dirty = true;
        }
        scheduleAsyncFlushOrSync();
    }

    public void removeIfSetAsync(String path) {
        if (path == null || path.isEmpty()) return;
        boolean changed;
        synchronized (VCarterUtils.class) {
            if (!dataConfig.isSet(path)) {
                return;
            }
            dataConfig.set(path, null);
            dirty = true;
            changed = true;
        }
        if (changed) {
            scheduleAsyncFlushOrSync();
        }
    }

    private void scheduleAsyncFlushOrSync() {
        if (plugin != null && plugin.isEnabled()) {
            if (saveQueued.compareAndSet(false, true)) {
                try {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, this::flushQueuedAsync);
                } catch (Throwable t) {
                    saveQueued.set(false);
                    saveSyncIfDirty();
                }
            }
        } else {
            saveSyncIfDirty();
        }
    }

    private void flushQueuedAsync() {
        synchronized (VCarterUtils.class) {
            saveQueued.set(false);
            if (!dirty) {
                return;
            }
            writeToDisk();
        }
    }

    public void saveSyncIfDirty() {
        synchronized (VCarterUtils.class) {
            if (!dirty && dataFile.exists()) {
                return;
            }
            writeToDisk();
        }
    }

    public void saveSync() {
        synchronized (VCarterUtils.class) {
            writeToDisk();
        }
    }

    public void reload() {
        synchronized (VCarterUtils.class) {
            ensureParentDir();
            if (dataFile.exists()) {
                this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            } else {
                this.dataConfig = new YamlConfiguration();
            }
            this.dirty = false;
        }
    }

    private void writeToDisk() {
        File tempFile = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
        try {
            ensureParentDir();
            dataConfig.save(tempFile);
            try {
                Files.move(tempFile.toPath(), dataFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException e) {
            if (tempFile.exists()) {
                tempFile.delete();
            }
            try {
                dataConfig.save(dataFile);
                dirty = false;
            } catch (IOException fallbackEx) {
                logger.warning("Could not save " + RELATIVE_PATH + ": " + fallbackEx.getMessage());
            }
        }
    }
}
