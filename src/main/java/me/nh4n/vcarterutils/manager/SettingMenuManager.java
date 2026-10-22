package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

public class SettingMenuManager {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    public SettingMenuManager(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void reload() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            Inventory top = online.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof SettingMenuHolder holder) {
                populateInventory(online, top, holder);
            } else if (top.getHolder() instanceof AdminSettingMenuHolder adminHolder) {
                populateAdminInventory( top, adminHolder);
            }
        }
    }

    // ==============================================================
    //                    PLAYER /SETTING MENU
    // ==============================================================

    public void openSettingMenu(Player player) {
        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("setting-menu.rows", 3)));
        int size = rows * 9;

        String titleRaw = plugin.getConfig().getString(
                "setting-menu.title",
                "<#6c7086>✦ <#89dceb><b>Player Settings</b> <#6c7086>✦"
        );
        Component title = miniMessage.deserialize(titleRaw);

        SettingMenuHolder holder = new SettingMenuHolder(size, title);
        populateInventory(player, holder.getInventory(), holder);
        player.openInventory(holder.getInventory());
    }

    public void refreshMenu(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof SettingMenuHolder holder) {
            populateInventory(player, holder.getInventory(), holder);
        }
    }

    private void populateInventory(Player player, Inventory inv, SettingMenuHolder holder) {
        inv.clear();
        holder.clearSlotActions();

        if (plugin.getConfig().getBoolean("setting-menu.filler.enabled", true)) {
            String fillerMatName = plugin.getConfig().getString("setting-menu.filler.material", "GRAY_STAINED_GLASS_PANE");
            Material fillerMat = Material.matchMaterial(fillerMatName != null ? fillerMatName : "GRAY_STAINED_GLASS_PANE");
            if (fillerMat == null) {
                fillerMat = Material.GRAY_STAINED_GLASS_PANE;
            }

            ItemStack filler = createSimpleItem(fillerMat, plugin.getConfig().getString("setting-menu.filler.name", " "), List.of());
            for (int i = 0; i < inv.getSize(); i++) {
                inv.setItem(i, filler);
            }
        }

        ConfigurationSection itemsSec = plugin.getConfig().getConfigurationSection("setting-menu.items");
        if (itemsSec == null) {
            return;
        }

        for (String key : itemsSec.getKeys(false)) {
            ConfigurationSection itemSec = itemsSec.getConfigurationSection(key);
            if (itemSec == null || !itemSec.getBoolean("enabled", true)) {
                continue;
            }

            int slot = itemSec.getInt("slot", -1);
            if (slot < 0 || slot >= inv.getSize()) {
                continue;
            }

            String type = itemSec.getString("type", "DECORATION").trim().toUpperCase();
            if (type.equals("ADMIN_MENU") && !player.hasPermission("vcarterutils.admin")) {
                continue;
            }

            String matName = itemSec.getString("material", "ENDER_PEARL");
            Material mat = Material.matchMaterial(matName != null ? matName : "ENDER_PEARL");
            if (mat == null || mat == Material.AIR) {
                mat = Material.STONE;
            }

            ItemStack stack = new ItemStack(mat);
            ItemMeta meta = stack.getItemMeta();

            if (meta != null) {
                String nameRaw = itemSec.getString("name", "<#89dceb><b>Setting Item</b>");
                meta.displayName(miniMessage.deserialize(applyPlaceholders(player, type, nameRaw)));

                List<String> loreRaw = itemSec.getStringList("lore");
                List<Component> loreComp = new ArrayList<>();
                for (String line : loreRaw) {
                    loreComp.add(miniMessage.deserialize(applyPlaceholders(player, type, line)));
                }
                meta.lore(loreComp);

                int customModelData = itemSec.getInt("custom-model-data", 0);
                if (customModelData > 0) {
                    meta.setCustomModelData(customModelData);
                }

                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                stack.setItemMeta(meta);
            }

            if (itemSec.getBoolean("show-seconds-as-amount", true)) {
                if (type.equals("PEARL_COOLDOWN")) {
                    int currentSec = plugin.getPearlManager().getPlayerPersonalSeconds(player.getUniqueId());
                    stack.setAmount(Math.max(1, Math.min(64, currentSec)));
                } else if (type.equals("MACE_COOLDOWN")) {
                    int currentSec = plugin.getMaceManager().getPlayerPersonalSeconds(player.getUniqueId());
                    stack.setAmount(Math.max(1, Math.min(64, currentSec)));
                }
            }

            inv.setItem(slot, stack);
            holder.setSlotAction(slot, new MenuItemAction(key, type, itemSec.getString("command", "")));
        }
    }

    // ==============================================================
    //                  ADMIN /SETTING ADMIN MENU
    // ==============================================================

    public void openAdminSettingMenu(Player admin, int page) {
        Component title = miniMessage.deserialize("<#6c7086>✦ <#f38ba8><b>Admin Cooldown Manager</b> <#6c7086>✦");
        AdminSettingMenuHolder holder = new AdminSettingMenuHolder(54, title, Math.max(0, page));
        populateAdminInventory(holder.getInventory(), holder);
        admin.openInventory(holder.getInventory());
    }

    public void refreshAdminMenu(Player admin) {
        if (admin.getOpenInventory().getTopInventory().getHolder() instanceof AdminSettingMenuHolder holder) {
            populateAdminInventory(holder.getInventory(), holder);
        }
    }

    private void populateAdminInventory(Inventory inv, AdminSettingMenuHolder holder) {
        inv.clear();
        holder.clearSlotActions();

        ItemStack borderFiller = createSimpleItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        ItemStack separatorFiller = createSimpleItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());

        // Row 1 (0-8) & Row 6 (45-53): Black glass border
        for (int i = 0; i <= 8; i++) inv.setItem(i, borderFiller);
        for (int i = 45; i <= 53; i++) inv.setItem(i, borderFiller);
        // Row 2 (9-17): Gray separator
        for (int i = 9; i <= 17; i++) inv.setItem(i, separatorFiller);

        PearlManager pearlManager = plugin.getPearlManager();
        MaceManager maceManager = plugin.getMaceManager();

        // --- SLOT 2: GLOBAL PEARL SETTING ---
        boolean pearlGlobalOn = pearlManager.isGlobalForceEnabled();
        int pearlGlobalSec = pearlManager.getGlobalForceSeconds();
        int pearlDefSec = pearlManager.getDefaultSeconds();

        ItemStack globalPearlItem = createSimpleItem(
                Material.ENDER_PEARL,
                "<#89dceb><b><!italic>Global Ender Pearl Settings</b>",
                List.of(
                        "<#6c7086><!italic>Manage server-wide Pearl cooldowns.",
                        "",
                        "<#a6adc8><!italic>Global Force Override: " + (pearlGlobalOn ? "<#a6e3a1><b>ENABLED</b>" : "<#f38ba8><b>DISABLED</b>"),
                        "<#a6adc8><!italic>Global Force Duration: <#f9e2af><b>" + pearlGlobalSec + "s</b>",
                        "<#a6adc8><!italic>Default Player Cooldown: <#89dceb><b>" + pearlDefSec + "s</b>",
                        "",
                        "<#a6adc8><!italic>Options: " + buildOptionsBar(pearlGlobalSec, pearlManager.getAvailableOptions()),
                        "",
                        "<#a6e3a1><!italic>▸ Left-Click <#a6adc8>to increase duration",
                        "<#f9e2af><!italic>▸ Right-Click <#a6adc8>to decrease duration",
                        "<#89dceb><!italic>▸ Shift + Left-Click <#a6adc8>to toggle Global Force",
                        "<#f38ba8><!italic>▸ Shift + Right-Click <#a6adc8>to set as Default (" + pearlGlobalSec + "s)"
                )
        );
        globalPearlItem.setAmount(Math.max(1, Math.min(64, pearlGlobalSec)));
        inv.setItem(2, globalPearlItem);
        holder.setSlotAction(2, new AdminSlotAction("GLOBAL_PEARL", null));

        // --- SLOT 4: WORLD SCANNER INFO ---
        List<DiscoveredWorld> allWorlds = scanAllWorlds();
        int totalPages = Math.max(1, (int) Math.ceil(allWorlds.size() / 27.0));
        int currentPage = Math.max(0, Math.min(holder.getPage(), totalPages - 1));
        holder.setPage(currentPage);

        ItemStack scannerItem = createSimpleItem(
                Material.RECOVERY_COMPASS,
                "<#a6e3a1><b><!italic>World Cooldown Scanner</b>",
                List.of(
                        "<#6c7086><!italic>Auto-detected from Bukkit, Multiverse",
                        "<#6c7086><!italic>& AdvancedSlimeWorldManager (ASWM).",
                        "",
                        "<#a6adc8><!italic>Total Worlds Found: <#89dceb><b>" + allWorlds.size() + "</b>",
                        "<#a6adc8><!italic>Current Page: <#f9e2af><b>" + (currentPage + 1) + "/" + totalPages + "</b>",
                        "",
                        "<#a6e3a1><!italic>▸ Click <#a6adc8>to rescan worlds & refresh"
                )
        );
        inv.setItem(4, scannerItem);
        holder.setSlotAction(4, new AdminSlotAction("RESCAN", null));

        // --- SLOT 6: GLOBAL MACE SETTING ---
        boolean maceGlobalOn = maceManager.isGlobalForceEnabled();
        int maceGlobalSec = maceManager.getGlobalForceSeconds();
        int maceDefSec = maceManager.getDefaultSeconds();

        ItemStack globalMaceItem = createSimpleItem(
                Material.MACE,
                "<#89dceb><b><!italic>Global Mace Settings</b>",
                List.of(
                        "<#6c7086><!italic>Manage server-wide Mace cooldowns.",
                        "",
                        "<#a6adc8><!italic>Global Force Override: " + (maceGlobalOn ? "<#a6e3a1><b>ENABLED</b>" : "<#f38ba8><b>DISABLED</b>"),
                        "<#a6adc8><!italic>Global Force Duration: <#f9e2af><b>" + maceGlobalSec + "s</b>",
                        "<#a6adc8><!italic>Default Player Cooldown: <#89dceb><b>" + maceDefSec + "s</b>",
                        "",
                        "<#a6adc8><!italic>Options: " + buildOptionsBar(maceGlobalSec, maceManager.getAvailableOptions()),
                        "",
                        "<#a6e3a1><!italic>▸ Left-Click <#a6adc8>to increase duration",
                        "<#f9e2af><!italic>▸ Right-Click <#a6adc8>to decrease duration",
                        "<#89dceb><!italic>▸ Shift + Left-Click <#a6adc8>to toggle Global Force",
                        "<#f38ba8><!italic>▸ Shift + Right-Click <#a6adc8>to set as Default (" + maceGlobalSec + "s)"
                )
        );
        globalMaceItem.setAmount(Math.max(1, Math.min(64, maceGlobalSec)));
        inv.setItem(6, globalMaceItem);
        holder.setSlotAction(6, new AdminSlotAction("GLOBAL_MACE", null));

        // --- SLOTS 18..44: WORLD LIST (27 WORLDS PER PAGE) ---
        int startIndex = currentPage * 27;
        int endIndex = Math.min(startIndex + 27, allWorlds.size());

        int guiSlot = 18;
        for (int i = startIndex; i < endIndex; i++) {
            DiscoveredWorld dw = allWorlds.get(i);
            String wName = dw.name();

            int forcedPearl = pearlManager.getForcedWorldSeconds(wName);
            int forcedMace = maceManager.getForcedWorldSeconds(wName);
            boolean hasAnyForce = (forcedPearl >= 0 || forcedMace >= 0);

            Material iconMat = getWorldIconMaterial(dw, hasAnyForce);

            String pearlDisplay = (forcedPearl >= 0)
                    ? "<#a6e3a1><b>Forced (" + forcedPearl + "s)</b>"
                    : "<#6c7086>OFF (Uses Global/Player)";
            String maceDisplay = (forcedMace >= 0)
                    ? "<#a6e3a1><b>Forced (" + forcedMace + "s)</b>"
                    : "<#6c7086>OFF (Uses Global/Player)";

            List<String> wLore = List.of(
                    "<#6c7086><!italic>Status: " + (dw.loaded() ? "<#a6e3a1>Loaded" : "<#f9e2af>Unloaded") + " <#6c7086>(" + dw.source() + ")",
                    "",
                    "<#a6adc8><!italic>Pearl Force Cooldown: " + pearlDisplay,
                    "<#a6adc8><!italic>Mace Force Cooldown: " + maceDisplay,
                    "",
                    "<#89dceb><!italic>▸ Left-Click <#a6adc8>to cycle Pearl Force",
                    "<#f9e2af><!italic>▸ Right-Click <#a6adc8>to cycle Mace Force",
                    "<#f38ba8><!italic>▸ Shift + Left-Click <#a6adc8>to turn Pearl Force OFF",
                    "<#f38ba8><!italic>▸ Shift + Right-Click <#a6adc8>to turn Mace Force OFF"
            );

            ItemStack worldItem = createSimpleItem(
                    iconMat,
                    (hasAnyForce ? "<#a6e3a1><b><!italic>✦ " : "<#89dceb><b><!italic>● ") + wName + "</b>",
                    wLore
            );

            inv.setItem(guiSlot, worldItem);
            holder.setSlotAction(guiSlot, new AdminSlotAction("WORLD_ITEM", wName));
            guiSlot++;
        }

        // --- BOTTOM NAVIGATION BAR (SLOTS 45..53) ---
        if (currentPage > 0) {
            ItemStack prevBtn = createSimpleItem(
                    Material.ARROW,
                    "<#89dceb><b><!italic>« Previous Page</b>",
                    List.of("<#a6adc8><!italic>Go to page " + currentPage)
            );
            inv.setItem(45, prevBtn);
            holder.setSlotAction(45, new AdminSlotAction("PREV_PAGE", null));
        }

        ItemStack playerMenuBtn = createSimpleItem(
                Material.BOOK,
                "<#89dceb><b><!italic>Open Player Settings</b>",
                List.of("<#6c7086><!italic>Click to return to your personal /setting menu.")
        );
        inv.setItem(48, playerMenuBtn);
        holder.setSlotAction(48, new AdminSlotAction("OPEN_PLAYER_MENU", null));

        ItemStack closeBtn = createSimpleItem(
                Material.BARRIER,
                "<#f38ba8><b><!italic>Close Menu</b>",
                List.of("<#6c7086><!italic>Click to close the admin menu.")
        );
        inv.setItem(49, closeBtn);
        holder.setSlotAction(49, new AdminSlotAction("CLOSE", null));

        if (currentPage + 1 < totalPages) {
            ItemStack nextBtn = createSimpleItem(
                    Material.ARROW,
                    "<#89dceb><b><!italic>Next Page »</b>",
                    List.of("<#a6adc8><!italic>Go to page " + (currentPage + 2))
            );
            inv.setItem(53, nextBtn);
            holder.setSlotAction(53, new AdminSlotAction("NEXT_PAGE", null));
        }
    }

    private Material getWorldIconMaterial(DiscoveredWorld dw, boolean hasAnyForce) {
        if (hasAnyForce) {
            return Material.EMERALD_BLOCK;
        }
        World w = Bukkit.getWorld(dw.name());
        if (w != null) {
            return switch (w.getEnvironment()) {
                case NETHER -> Material.NETHERRACK;
                case THE_END -> Material.END_STONE;
                default -> dw.source().contains("Slime") ? Material.SLIME_BLOCK : Material.GRASS_BLOCK;
            };
        }
        return dw.source().contains("Slime") ? Material.SLIME_BALL : Material.ENDER_EYE;
    }

    /**
     * Quet toan bo World tu:
     * 1. Bukkit.getWorlds() (dang load)
     * 2. Multiverse-Core (Reflection + worlds.yml)
     * 3. AdvancedSlimeWorldManager / SlimeWorldManager / SlimeWorldPlugin (worlds.yml + slime_worlds/*.slime)
     * 4. Cac world da cau hinh trong config.yml
     */
    public List<DiscoveredWorld> scanAllWorlds() {
        Map<String, DiscoveredWorld> map = new LinkedHashMap<>();

        // 1. Bukkit loaded worlds
        for (World w : Bukkit.getWorlds()) {
            String name = w.getName();
            map.put(name.toLowerCase(), new DiscoveredWorld(name, true, "Bukkit"));
        }

        File pluginsFolder = plugin.getDataFolder().getParentFile();

        // 2. Multiverse-Core (worlds.yml + API Reflection)
        if (pluginsFolder != null) {
            File mvWorldsFile = new File(pluginsFolder, "Multiverse-Core/worlds.yml");
            if (mvWorldsFile.exists()) {
                YamlConfiguration mvCfg = YamlConfiguration.loadConfiguration(mvWorldsFile);
                ConfigurationSection sec = mvCfg.getConfigurationSection("worlds");
                if (sec != null) {
                    for (String wName : sec.getKeys(false)) {
                        addDiscoveredWorld(map, wName, "Multiverse");
                    }
                }
            }
        }
        scanMultiverseReflection(map);

        // 3. AdvancedSlimeWorldManager / SlimeWorldManager / SlimeWorldPlugin
        if (pluginsFolder != null) {
            String[] slimeConfigPaths = {
                    "AdvancedSlimeWorldManager/worlds.yml",
                    "SlimeWorldManager/worlds.yml",
                    "SlimeWorldPlugin/worlds.yml"
            };
            for (String relPath : slimeConfigPaths) {
                File swmFile = new File(pluginsFolder, relPath);
                if (swmFile.exists()) {
                    YamlConfiguration swmCfg = YamlConfiguration.loadConfiguration(swmFile);
                    ConfigurationSection sec = swmCfg.getConfigurationSection("worlds");
                    if (sec != null) {
                        for (String wName : sec.getKeys(false)) {
                            addDiscoveredWorld(map, wName, "ASWM/Slime");
                        }
                    }
                }
            }
        }

        // Quet thu muc slime_worlds/*.slime o thu muc goc server neu co
        File slimeFolder = new File(Bukkit.getWorldContainer(), "slime_worlds");
        if (slimeFolder.exists() && slimeFolder.isDirectory()) {
            File[] files = slimeFolder.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().endsWith(".slime")) {
                        String wName = f.getName().substring(0, f.getName().length() - 6);
                        addDiscoveredWorld(map, wName, "ASWM/Slime");
                    }
                }
            }
        }

        // 4. Cac world dang duoc khai bao trong config.yml
        for (String wName : plugin.getPearlManager().getConfiguredForcedWorldNames()) {
            addDiscoveredWorld(map, wName, "Config");
        }
        for (String wName : plugin.getMaceManager().getConfiguredForcedWorldNames()) {
            addDiscoveredWorld(map, wName, "Config");
        }

        List<DiscoveredWorld> result = new ArrayList<>(map.values());
        result.sort(Comparator.comparing((DiscoveredWorld d) -> !d.loaded())
                .thenComparing(d -> d.name().toLowerCase()));
        return result;
    }

    private void addDiscoveredWorld(Map<String, DiscoveredWorld> map, String worldName, String source) {
        if (worldName == null || worldName.trim().isEmpty()) {
            return;
        }
        String clean = worldName.trim();
        String lower = clean.toLowerCase();
        DiscoveredWorld existing = map.get(lower);
        if (existing == null) {
            boolean isLoaded = (Bukkit.getWorld(clean) != null);
            map.put(lower, new DiscoveredWorld(clean, isLoaded, source));
        } else if (!existing.source().contains(source) && !source.equals("Config")) {
            map.put(lower, new DiscoveredWorld(existing.name(), existing.loaded(), existing.source() + "/" + source));
        }
    }

    private void scanMultiverseReflection(Map<String, DiscoveredWorld> map) {
        try {
            Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
            if (mv == null || !mv.isEnabled()) {
                return;
            }
            Method getMVWorldManager = mv.getClass().getMethod("getMVWorldManager");
            Object worldManager = getMVWorldManager.invoke(mv);
            if (worldManager == null) {
                return;
            }

            Method getUnloadedWorlds = worldManager.getClass().getMethod("getUnloadedWorlds");
            Object unloadedObj = getUnloadedWorlds.invoke(worldManager);
            if (unloadedObj instanceof Collection<?> col) {
                for (Object item : col) {
                    if (item != null) {
                        addDiscoveredWorld(map, item.toString(), "Multiverse");
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private ItemStack createSimpleItem(Material material, String nameMiniMsg, List<String> loreMiniMsg) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (nameMiniMsg != null) {
                meta.displayName(miniMessage.deserialize(nameMiniMsg));
            }
            if (loreMiniMsg != null && !loreMiniMsg.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreMiniMsg) {
                    lore.add(miniMessage.deserialize(line));
                }
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String applyPlaceholders(Player player, String itemType, String input) {
        if (input == null) {
            return "";
        }

        int personalSec;
        int defaultSec;
        int effectiveSec;
        String worldStatus;
        String optionsBar;

        if ("MACE_COOLDOWN".equalsIgnoreCase(itemType)) {
            MaceManager maceManager = plugin.getMaceManager();
            personalSec = maceManager.getPlayerPersonalSeconds(player.getUniqueId());
            defaultSec = maceManager.getDefaultSeconds();
            effectiveSec = maceManager.getEffectiveCooldownSeconds(player);

            if (maceManager.isWorldForced(player.getWorld())) {
                String forcedFormat = plugin.getConfig().getString(
                        "mace-cooldown.messages.world-status-forced",
                        "<#f9e2af>⚠ Forced in this world: <#89dceb><b>%forced%s</b>"
                );
                worldStatus = forcedFormat.replace("%forced%", String.valueOf(effectiveSec))
                                          .replace("%world%", player.getWorld().getName());
            } else if (maceManager.isGlobalForceEnabled()) {
                String globalFormat = plugin.getConfig().getString(
                        "mace-cooldown.messages.world-status-global-forced",
                        "<#f9e2af>⚠ Forced globally: <#89dceb><b>%forced%s</b>"
                );
                worldStatus = globalFormat.replace("%forced%", String.valueOf(effectiveSec));
            } else {
                worldStatus = plugin.getConfig().getString(
                        "mace-cooldown.messages.world-status-normal",
                        "<#a6e3a1>✔ Active in current world"
                );
            }
            optionsBar = buildOptionsBar(personalSec, maceManager.getAvailableOptions());
        } else {
            PearlManager pearlManager = plugin.getPearlManager();
            personalSec = pearlManager.getPlayerPersonalSeconds(player.getUniqueId());
            defaultSec = pearlManager.getDefaultSeconds();
            effectiveSec = pearlManager.getEffectiveCooldownSeconds(player);

            if (pearlManager.isWorldForced(player.getWorld())) {
                String forcedFormat = plugin.getConfig().getString(
                        "pearl-cooldown.messages.world-status-forced",
                        "<#f9e2af>⚠ Forced in this world: <#89dceb><b>%forced%s</b>"
                );
                worldStatus = forcedFormat.replace("%forced%", String.valueOf(effectiveSec))
                                          .replace("%world%", player.getWorld().getName());
            } else if (pearlManager.isGlobalForceEnabled()) {
                String globalFormat = plugin.getConfig().getString(
                        "pearl-cooldown.messages.world-status-global-forced",
                        "<#f9e2af>⚠ Forced globally: <#89dceb><b>%forced%s</b>"
                );
                worldStatus = globalFormat.replace("%forced%", String.valueOf(effectiveSec));
            } else {
                worldStatus = plugin.getConfig().getString(
                        "pearl-cooldown.messages.world-status-normal",
                        "<#a6e3a1>✔ Active in current world"
                );
            }
            optionsBar = buildOptionsBar(personalSec, pearlManager.getAvailableOptions());
        }

        return input
                .replace("%player%", player.getName())
                .replace("%world%", player.getWorld().getName())
                .replace("%current%", String.valueOf(personalSec))
                .replace("%default%", String.valueOf(defaultSec))
                .replace("%effective%", String.valueOf(effectiveSec))
                .replace("%world_status%", worldStatus)
                .replace("%options_list%", optionsBar);
    }

    private String buildOptionsBar(int currentSec, List<Integer> options) {
        if (options.isEmpty()) {
            return "<#89dceb><b>" + currentSec + "s</b>";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < options.size(); i++) {
            int opt = options.get(i);
            if (i > 0) {
                sb.append(" <#6c7086>• ");
            }
            if (opt == currentSec) {
                sb.append("<#a6e3a1><b>[").append(opt).append("s]</b>");
            } else {
                sb.append("<#6c7086>").append(opt).append("s");
            }
        }
        return sb.toString();
    }

    public record DiscoveredWorld(String name, boolean loaded, String source) {
    }

    public record MenuItemAction(String key, String type, String command) {
    }

    public record AdminSlotAction(String actionType, String targetWorld) {
    }

    public static class SettingMenuHolder implements InventoryHolder {
        private final Inventory inventory;
        private final Map<Integer, MenuItemAction> slotActions = new HashMap<>();

        public SettingMenuHolder(int size, Component title) {
            this.inventory = Bukkit.createInventory(this, size, title);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void setSlotAction(int slot, MenuItemAction action) {
            slotActions.put(slot, action);
        }

        public MenuItemAction getSlotAction(int slot) {
            return slotActions.get(slot);
        }

        public void clearSlotActions() {
            slotActions.clear();
        }
    }

    public static class AdminSettingMenuHolder implements InventoryHolder {
        private final Inventory inventory;
        private final Map<Integer, AdminSlotAction> slotActions = new HashMap<>();
        private int page;

        public AdminSettingMenuHolder(int size, Component title, int page) {
            this.inventory = Bukkit.createInventory(this, size, title);
            this.page = page;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public void setSlotAction(int slot, AdminSlotAction action) {
            slotActions.put(slot, action);
        }

        public AdminSlotAction getSlotAction(int slot) {
            return slotActions.get(slot);
        }

        public void clearSlotActions() {
            slotActions.clear();
        }
    }
}
