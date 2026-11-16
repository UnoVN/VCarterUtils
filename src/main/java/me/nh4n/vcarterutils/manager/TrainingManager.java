package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.hook.FoCombatTagHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TrainingManager {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    private final Map<UUID, Boolean> masterSettings = new ConcurrentHashMap<>();
    private final Map<UUID, Map<TrainingOption, Boolean>> optionSettings = new ConcurrentHashMap<>();
    private final Set<String> cachedBlacklistedWorlds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile boolean cachedEnabled = true;
    private volatile boolean cachedDisableInCombat = true;
    private File dataFile;

    public TrainingManager(VCarterUtils plugin) {
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
        masterSettings.clear();
        optionSettings.clear();

        synchronized (VCarterUtils.class) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);

            // 1. Backward compatibility: read legacy training-mode-enabled.<UUID>
            ConfigurationSection legacySec = cfg.getConfigurationSection("training-mode-enabled");
            if (legacySec != null) {
                for (String key : legacySec.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        masterSettings.put(uuid, legacySec.getBoolean(key, false));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            // 2. Structured training data: training-mode.<UUID>.master & options
            ConfigurationSection sec = cfg.getConfigurationSection("training-mode");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        masterSettings.put(uuid, sec.getBoolean(key + ".master", false));
                        ConfigurationSection optSec = sec.getConfigurationSection(key + ".options");
                        if (optSec != null) {
                            Map<TrainingOption, Boolean> map = new ConcurrentHashMap<>();
                            for (TrainingOption opt : TrainingOption.values()) {
                                map.put(opt, optSec.getBoolean(opt.getKey(), true));
                            }
                            optionSettings.put(uuid, map);
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
    }

    public void savePlayerSettingsSync() {
        if (dataFile == null) return;
        synchronized (VCarterUtils.class) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
            for (Map.Entry<UUID, Boolean> entry : masterSettings.entrySet()) {
                String uuidStr = entry.getKey().toString();
                cfg.set("training-mode." + uuidStr + ".master", entry.getValue());
                cfg.set("training-mode-enabled." + uuidStr, entry.getValue());
            }
            for (Map.Entry<UUID, Map<TrainingOption, Boolean>> entry : optionSettings.entrySet()) {
                String uuidStr = entry.getKey().toString();
                for (Map.Entry<TrainingOption, Boolean> optEntry : entry.getValue().entrySet()) {
                    cfg.set("training-mode." + uuidStr + ".options." + optEntry.getKey().getKey(), optEntry.getValue());
                }
            }
            try {
                cfg.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save playerdata.yml: " + e.getMessage());
            }
        }
    }

    private void saveMasterToggleAsync(UUID uuid, boolean enabled) {
        if (dataFile == null) return;
        final File fileToSave = this.dataFile;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (VCarterUtils.class) {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(fileToSave);
                cfg.set("training-mode." + uuid + ".master", enabled);
                cfg.set("training-mode-enabled." + uuid, enabled);
                try {
                    cfg.save(fileToSave);
                } catch (IOException ignored) {
                }
            }
        });
    }

    private void saveOptionToggleAsync(UUID uuid, TrainingOption option, boolean enabled) {
        if (dataFile == null) return;
        final File fileToSave = this.dataFile;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (VCarterUtils.class) {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(fileToSave);
                cfg.set("training-mode." + uuid + ".options." + option.getKey(), enabled);
                try {
                    cfg.save(fileToSave);
                } catch (IOException ignored) {
                }
            }
        });
    }

    public void reload() {
        this.cachedEnabled = plugin.getConfig().getBoolean("training.enabled", true);
        this.cachedDisableInCombat = plugin.getConfig().getBoolean("training.disable-in-combat", true);
        this.cachedBlacklistedWorlds.clear();
        for (String w : plugin.getConfig().getStringList("training.blacklisted-worlds")) {
            if (w != null && !w.trim().isEmpty()) {
                this.cachedBlacklistedWorlds.add(w.trim().toLowerCase());
            }
        }
    }

    public boolean isWorldBlacklisted(String worldName) {
        if (worldName == null) return false;
        return cachedBlacklistedWorlds.contains(worldName.trim().toLowerCase());
    }

    // ==========================================
    //           MASTER SETTINGS
    // ==========================================

    public boolean isMasterEnabled(UUID uuid) {
        return masterSettings.getOrDefault(uuid, false);
    }

    public void setMasterEnabled(UUID uuid, boolean enabled) {
        masterSettings.put(uuid, enabled);
        saveMasterToggleAsync(uuid, enabled);
    }

    public boolean toggleMaster(UUID uuid) {
        boolean newState = !isMasterEnabled(uuid);
        setMasterEnabled(uuid, newState);
        return newState;
    }

    // ==========================================
    //          OPTION SETTINGS
    // ==========================================

    public boolean isOptionEnabled(UUID uuid, TrainingOption option) {
        Map<TrainingOption, Boolean> map = optionSettings.get(uuid);
        if (map == null) {
            return true; // Default to true if not explicitly disabled
        }
        return map.getOrDefault(option, true);
    }

    public void setOptionEnabled(UUID uuid, TrainingOption option, boolean enabled) {
        optionSettings.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(option, enabled);
        saveOptionToggleAsync(uuid, option, enabled);
    }

    public boolean toggleOption(UUID uuid, TrainingOption option) {
        boolean newState = !isOptionEnabled(uuid, option);
        setOptionEnabled(uuid, option, newState);
        return newState;
    }

    // ==========================================
    //       EFFECTIVE CHECKS & AUTO COMBAT
    // ==========================================

    /**
     * Checks if a specific Training Option is effectively active for the player right now.
     * Automatically auto-disables training mode if the player is in combat!
     */
    public boolean isOptionEffective(Player player, TrainingOption option) {
        if (player == null || !player.isOnline()) return false;
        if (player.getGameMode() == GameMode.CREATIVE) return false;
        if (!cachedEnabled) return false;

        // Check Master toggle first
        if (!isMasterEnabled(player.getUniqueId())) {
            return false;
        }

        // Check world blacklist
        if (isWorldBlacklisted(player.getWorld().getName())) {
            return false;
        }

        // Auto disable if player is genuinely tagged in active combat
        if (cachedDisableInCombat && FoCombatTagHook.isTagged(player)) {
            // Attempt to untag if it was an artifact from non-PvP actions (e.g. pearls, projectiles)
            FoCombatTagHook.untag(player);
            if (FoCombatTagHook.isTagged(player)) {
                disableTrainingOnCombat(player);
                return false;
            }
        }

        // Check individual option setting
        return isOptionEnabled(player.getUniqueId(), option);
    }

    /**
     * Compatibility helper: checks if master training mode is active for the player.
     */
    public boolean isPlayerEnabled(UUID uuid) {
        return isMasterEnabled(uuid);
    }

    /**
     * Automatically disables Training Mode for the player when they enter combat,
     * closes any open training GUI, plays a warning sound, and notifies them in chat.
     */
    public void disableTrainingOnCombat(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        if (isMasterEnabled(uuid)) {
            setMasterEnabled(uuid, false);

            // Close training menu if open
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof TrainingMenuHolder) {
                player.closeInventory();
            }

            // Warning sound
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);

            // Chat message
            String msg = plugin.getConfig().getString(
                    "training.messages.auto-disabled-combat",
                    "%prefix_combat%%c_error%<b>ᴛʀᴀɪɴɪɴɢ ᴅɪsᴀʙʟᴇᴅ</b> %c_muted%» %c_text%Automatically turned off due to PvP combat."
            );
            player.sendMessage(plugin.parseMessage(msg));
        }
    }

    // ==========================================
    //           TRAINING MODE GUI
    // ==========================================

    public static final int MENU_SIZE = 36;
    public static final int BACK_SLOT = 31;

    public void openTrainingMenu(Player player) {
        String titleRaw = plugin.getConfig().getString(
                "training-menu.title",
                "<#6c7086>✦ <#89dceb><b>ᴛʀᴀɪɴɪɴɢ sᴇᴛᴛɪɴɢs</b> <#6c7086>✦"
        );
        Component title = plugin.parseMessage(titleRaw);
        TrainingMenuHolder holder = new TrainingMenuHolder(MENU_SIZE, title);
        populateTrainingInventory(player, holder.getInventory());
        player.openInventory(holder.getInventory());
    }

    public void refreshTrainingMenu(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof TrainingMenuHolder holder) {
            populateTrainingInventory(player, holder.getInventory());
        }
    }

    private void populateTrainingInventory(Player player, Inventory inv) {
        inv.clear();

        // 1. Unified 2-tone Catppuccin frame (black header/footer bars, gray inner background)
        ItemStack border = createSimpleItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        ItemStack filler = createSimpleItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            boolean isEdgeRow = (i < 9 || i >= size - 9);
            inv.setItem(i, isEdgeRow ? border : filler);
        }

        UUID uuid = player.getUniqueId();
        boolean masterOn = isMasterEnabled(uuid);

        // 2. Slot 4: Master Training Switch
        String masterStatus = masterOn ? "<#a6e3a1><b>✔ ᴇɴᴀʙʟᴇᴅ</b>" : "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ</b>";
        ItemStack masterItem = createSimpleItem(
                Material.NETHER_STAR,
                "<!italic><#89dceb><b>Master Training Mode</b>",
                List.of(
                        "<!italic><#6c7086>ᴍᴀsᴛᴇʀ ᴄᴏɴᴛʀᴏʟ",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>sᴛᴀᴛᴜs: " + masterStatus,
                        "",
                        "<!italic><#a6adc8>Master switch for all training features.",
                        "<!italic><#a6adc8>When disabled, options below are inactive.",
                        "",
                        "<!italic><white><glyph:left_click></white> <#89dceb>ᴛᴏɢɢʟᴇ ᴍᴀsᴛᴇʀ sᴡɪᴛᴄʜ"
                )
        );
        inv.setItem(4, masterItem);

        // 3. Slots: Training Options (2x4 aligned grid: 10,12,14,16 and 19,21,23,25)
        for (TrainingOption option : TrainingOption.values()) {
            boolean optionOn = isOptionEnabled(uuid, option);
            String optStatus;
            if (!masterOn) {
                optStatus = optionOn
                        ? "<#f9e2af><b>● ᴇɴᴀʙʟᴇᴅ</b> <#6c7086>(ᴍᴀsᴛᴇʀ ᴏꜰꜰ)"
                        : "<#6c7086><b>○ ᴅɪsᴀʙʟᴇᴅ</b> <#6c7086>(ᴍᴀsᴛᴇʀ ᴏꜰꜰ)";
            } else {
                optStatus = optionOn ? "<#a6e3a1><b>✔ ᴇɴᴀʙʟᴇᴅ</b>" : "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ</b>";
            }

            List<String> lore = new ArrayList<>();
            lore.add("<!italic><#6c7086>ᴘʀᴀᴄᴛɪᴄᴇ ᴏᴘᴛɪᴏɴ");
            lore.add("");
            lore.add("<!italic><#6c7086>▪ <#a6adc8>sᴛᴀᴛᴜs: " + optStatus);
            lore.add("");
            for (String descLine : option.getDescriptionLines()) {
                lore.add("<!italic><#a6adc8>" + descLine);
            }
            lore.add("");
            lore.add("<!italic><white><glyph:left_click></white> <#89dceb>ᴛᴏɢɢʟᴇ ᴏɴ/ᴏꜰꜰ");

            ItemStack optItem = createSimpleItem(
                    option.getIcon(),
                    "<!italic>" + option.getSpriteTag() + " <#89dceb><b>" + option.getDisplayName() + "</b>",
                    lore
            );
            inv.setItem(option.getSlot(), optItem);
        }

        // 4. Slot 31: Back to main Settings menu
        ItemStack backBtn = createSimpleItem(
                "nexo:previous",
                Material.ARROW,
                "<!italic><#89dceb><b>« Back to Settings</b>",
                List.of("<!italic><white><glyph:left_click></white> <#a6adc8>ʀᴇᴛᴜʀɴ ᴛᴏ /sᴇᴛᴛɪɴɢ")
        );
        inv.setItem(BACK_SLOT, backBtn);
    }

    private ItemStack createSimpleItem(Material mat, String name, List<String> lore) {
        return createSimpleItem(null, mat, name, lore);
    }

    private ItemStack createSimpleItem(String nexoId, Material fallbackMat, String name, List<String> lore) {
        ItemStack item = me.nh4n.vcarterutils.hook.NexoHook.createItem(nexoId, fallbackMat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.parseMessage(name));
            List<Component> compLore = new ArrayList<>();
            for (String l : lore) {
                compLore.add(plugin.parseMessage(l));
            }
            meta.lore(compLore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void clearAll() {
        savePlayerSettingsSync();
        masterSettings.clear();
        optionSettings.clear();
    }

    // ==========================================
    //           INVENTORY HOLDER
    // ==========================================

    public static class TrainingMenuHolder implements InventoryHolder {
        private final Inventory inventory;

        public TrainingMenuHolder(int size, Component title) {
            this.inventory = Bukkit.createInventory(this, size, title);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
