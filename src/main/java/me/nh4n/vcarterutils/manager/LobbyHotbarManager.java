package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LobbyHotbarManager {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;
    private final NamespacedKey lobbyCmdKey;

    private final Set<String> lobbyWorlds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Boolean> playerToggleSettings = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> savedHotbars = new ConcurrentHashMap<>();
    private final Set<UUID> respawningPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private File dataFile;

    public LobbyHotbarManager(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.lobbyCmdKey = new NamespacedKey(plugin, "vcarter_lobby_cmd");
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
        loadFromDataFile();
    }

    private void loadFromDataFile() {
        playerToggleSettings.clear();
        savedHotbars.clear();
        synchronized (VCarterUtils.class) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);

            ConfigurationSection toggleSec = cfg.getConfigurationSection("lobby-hotbar-enabled");
            if (toggleSec != null) {
                for (String key : toggleSec.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        playerToggleSettings.put(uuid, toggleSec.getBoolean(key, true));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            ConfigurationSection savedSec = cfg.getConfigurationSection("saved-hotbars");
            if (savedSec != null) {
                for (String key : savedSec.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        ConfigurationSection pSec = savedSec.getConfigurationSection(key);
                        if (pSec != null) {
                            ItemStack[] hotbar = new ItemStack[9];
                            boolean hasAny = false;
                            for (int i = 0; i < 9; i++) {
                                hotbar[i] = pSec.getItemStack("slot_" + i, null);
                                if (hotbar[i] != null && hotbar[i].getType() != Material.AIR) {
                                    hasAny = true;
                                }
                            }
                            if (hasAny) {
                                savedHotbars.put(uuid, hotbar);
                            }
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
    }

    public void saveAllDataSync() {
        if (dataFile == null) return;
        synchronized (VCarterUtils.class) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
            for (Map.Entry<UUID, Boolean> entry : playerToggleSettings.entrySet()) {
                cfg.set("lobby-hotbar-enabled." + entry.getKey().toString(), entry.getValue());
            }
            cfg.set("saved-hotbars", null);
            for (Map.Entry<UUID, ItemStack[]> entry : savedHotbars.entrySet()) {
                String base = "saved-hotbars." + entry.getKey().toString();
                ItemStack[] bar = entry.getValue();
                for (int i = 0; i < 9; i++) {
                    cfg.set(base + ".slot_" + i, bar[i]);
                }
            }
            try {
                cfg.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save playerdata.yml: " + e.getMessage());
            }
        }
    }

    private void savePlayerHotbarAsync(UUID uuid, ItemStack[] hotbar) {
        if (dataFile == null) return;
        final File fileToSave = this.dataFile;
        final ItemStack[] copy = (hotbar != null) ? cloneHotbar(hotbar) : null;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (VCarterUtils.class) {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(fileToSave);
                String base = "saved-hotbars." + uuid.toString();
                if (copy == null) {
                    cfg.set(base, null);
                } else {
                    for (int i = 0; i < 9; i++) {
                        cfg.set(base + ".slot_" + i, copy[i]);
                    }
                }
                try {
                    cfg.save(fileToSave);
                } catch (IOException ignored) {
                }
            }
        });
    }

    private void savePlayerToggleAsync(UUID uuid, boolean enabled) {
        if (dataFile == null) return;
        final File fileToSave = this.dataFile;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (VCarterUtils.class) {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(fileToSave);
                cfg.set("lobby-hotbar-enabled." + uuid.toString(), enabled);
                try {
                    cfg.save(fileToSave);
                } catch (IOException ignored) {
                }
            }
        });
    }

    public void reload() {
        lobbyWorlds.clear();
        List<String> list = plugin.getConfig().getStringList("lobby-hotbar.worlds");
        for (String w : list) {
            if (w != null && !w.trim().isEmpty()) {
                lobbyWorlds.add(w.trim().toLowerCase());
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (isLobbyWorld(online.getWorld()) && isEnabled() && isPlayerEnabled(online.getUniqueId())) {
                applyLobbyHotbar(online);
            } else if (hasSavedHotbar(online.getUniqueId()) || hasAnyLobbyItem(online)) {
                restorePlayerHotbar(online);
            }
        }
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("lobby-hotbar.enabled", true);
    }

    public boolean isLobbyWorld(World world) {
        if (world == null) return false;
        return lobbyWorlds.contains(world.getName().trim().toLowerCase());
    }

    public void markRespawning(UUID uuid) {
        respawningPlayers.add(uuid);
        savedHotbars.remove(uuid);
        savePlayerHotbarAsync(uuid, null);
    }

    public void unmarkRespawning(UUID uuid) {
        respawningPlayers.remove(uuid);
    }

    public boolean isRespawning(UUID uuid) {
        return respawningPlayers.contains(uuid);
    }

    public boolean toggleLobbyWorld(String worldName) {
        if (worldName == null || worldName.trim().isEmpty()) return false;
        String clean = worldName.trim();
        String lower = clean.toLowerCase();

        List<String> currentList = new ArrayList<>(plugin.getConfig().getStringList("lobby-hotbar.worlds"));
        boolean removed = currentList.removeIf(w -> w != null && w.trim().equalsIgnoreCase(clean));

        boolean added;
        if (removed) {
            lobbyWorlds.remove(lower);
            added = false;
        } else {
            currentList.add(clean);
            lobbyWorlds.add(lower);
            added = true;
        }

        plugin.getConfig().set("lobby-hotbar.worlds", currentList);
        plugin.saveConfig();
        reload();
        return added;
    }

    public boolean isPlayerEnabled(UUID uuid) {
        return playerToggleSettings.getOrDefault(uuid, true);
    }

    public boolean togglePlayerSetting(Player player) {
        UUID uuid = player.getUniqueId();
        boolean next = !isPlayerEnabled(uuid);
        playerToggleSettings.put(uuid, next);
        savePlayerToggleAsync(uuid, next);

        if (isLobbyWorld(player.getWorld())) {
            if (next && isEnabled()) {
                applyLobbyHotbar(player);
            } else {
                restorePlayerHotbar(player);
            }
        }
        return next;
    }

    public boolean hasSavedHotbar(UUID uuid) {
        return savedHotbars.containsKey(uuid);
    }

    public void applyLobbyHotbar(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!isEnabled() || !isPlayerEnabled(player.getUniqueId()) || !isLobbyWorld(player.getWorld())) {
            return;
        }

        UUID uuid = player.getUniqueId();
        PlayerInventory inv = player.getInventory();

        // Kiem tra xem tren hotbar (0-8) hien tai co vat pham that nao khong
        boolean hasRealItemsInHotbar = false;
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR && !isLobbyItem(item)) {
                hasRealItemsInHotbar = true;
                break;
            }
        }

        // Neu co vat pham that tren hotbar -> Luu/Cap nhat day du 9 o vao savedHotbars
        if (hasRealItemsInHotbar) {
            ItemStack[] currentBar = new ItemStack[9];
            for (int i = 0; i < 9; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() != Material.AIR && !isLobbyItem(item)) {
                    currentBar[i] = item.clone();
                } else {
                    currentBar[i] = null;
                }
            }
            savedHotbars.put(uuid, currentBar);
            savePlayerHotbarAsync(uuid, currentBar);
        }

        // Xoa sach 9 o hotbar de dat Lobby Hotbar
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, null);
        }

        ConfigurationSection itemsSec = plugin.getConfig().getConfigurationSection("lobby-hotbar.items");
        if (itemsSec == null) return;

        boolean canUseBack = canPlayerUseBack(player);
        int backItemSlot = -1;

        for (String key : itemsSec.getKeys(false)) {
            ConfigurationSection itemSec = itemsSec.getConfigurationSection(key);
            if (itemSec == null || !itemSec.getBoolean("enabled", true)) continue;

            boolean isBackItem = itemSec.getBoolean("only-show-when-can-back", false);
            if (isBackItem && !canUseBack) {
                continue;
            }

            int slot = itemSec.getInt("slot", -1);
            if (slot < 0 || slot > 8) continue;

            String matName = itemSec.getString("material", "COMPASS");
            Material mat = Material.matchMaterial(matName != null ? matName : "COMPASS");
            if (mat == null || mat == Material.AIR) mat = Material.COMPASS;

            String cmd = itemSec.getString("command", "");
            String nameRaw = itemSec.getString("name", "<!italic><#89dceb><b>Item</b>");
            List<String> loreRaw = itemSec.getStringList("lore");

            ItemStack stack = new ItemStack(mat, 1);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(miniMessage.deserialize(nameRaw));
                List<Component> loreComp = new ArrayList<>();
                for (String line : loreRaw) {
                    loreComp.add(miniMessage.deserialize(line));
                }
                meta.lore(loreComp);

                int cmdModel = itemSec.getInt("custom-model-data", 0);
                if (cmdModel > 0) {
                    meta.setCustomModelData(cmdModel);
                }

                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                meta.getPersistentDataContainer().set(lobbyCmdKey, PersistentDataType.STRING, cmd != null ? cmd : "");
                stack.setItemMeta(meta);
            }

            inv.setItem(slot, stack);

            if (isBackItem && canUseBack) {
                backItemSlot = slot;
            }
        }

        // Neu co item /back (o chinh giua - slot 4 / o so 5), luon force select o do de nguoi choi chi can Right-Click
        if (backItemSlot >= 0 && backItemSlot <= 8) {
            inv.setHeldItemSlot(backItemSlot);
        }
    }

    public void restorePlayerHotbar(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        PlayerInventory inv = player.getInventory();

        removeAllLobbyItems(inv);

        ItemStack[] saved = savedHotbars.remove(uuid);
        if (saved != null) {
            for (int i = 0; i < 9; i++) {
                inv.setItem(i, saved[i]);
            }
            savePlayerHotbarAsync(uuid, null);
        }
    }

    public void onPlayerLoadKit(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        removeAllLobbyItems(player.getInventory());
        if (savedHotbars.remove(uuid) != null) {
            savePlayerHotbarAsync(uuid, null);
        }
    }

    public void removeAllLobbyItems(PlayerInventory inv) {
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isLobbyItem(contents[i])) {
                inv.setItem(i, null);
            }
        }
        if (isLobbyItem(inv.getItemInOffHand())) {
            inv.setItemInOffHand(null);
        }
    }

    public boolean hasAnyLobbyItem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isLobbyItem(item)) return true;
        }
        return isLobbyItem(player.getInventory().getItemInOffHand());
    }

    public boolean isLobbyItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(lobbyCmdKey, PersistentDataType.STRING);
    }

    public String getLobbyItemCommand(ItemStack item) {
        if (!isLobbyItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(lobbyCmdKey, PersistentDataType.STRING);
    }

    private boolean canPlayerUseBack(Player player) {
        BackManager backManager = plugin.getBackManager();
        if (!backManager.isEnabled() || backManager.isWorldBlacklisted(player.getWorld())) {
            return false;
        }
        Location deathLoc = backManager.getDeathLocation(player.getUniqueId());
        return deathLoc != null && deathLoc.getWorld() != null && !backManager.isWorldBlacklisted(deathLoc.getWorld());
    }

    private ItemStack[] cloneHotbar(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[9];
        for (int i = 0; i < Math.min(9, source.length); i++) {
            copy[i] = (source[i] != null) ? source[i].clone() : null;
        }
        return copy;
    }

    public void clearAll() {
        saveAllDataSync();
        lobbyWorlds.clear();
        respawningPlayers.clear();
    }
}
