package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.hook.NexoHook;
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
                populateAdminInventory(top, adminHolder);
            }
        }
    }

    public void openSettingMenu(Player player) {
        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("setting-menu.rows", 3)));
        int size = rows * 9;

        String titleRaw = plugin.getConfig().getString(
                "setting-menu.title",
                "<#6c7086>✦ <#89dceb><b>Settings</b> <#6c7086>✦"
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
            ConfigurationSection fillerSec = plugin.getConfig().getConfigurationSection("setting-menu.filler");
            ItemStack filler = NexoHook.createItemFromSection(fillerSec, Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta fillerMeta = filler.getItemMeta();
            if (fillerMeta != null) {
                String fillerName = plugin.getConfig().getString("setting-menu.filler.name", " ");
                fillerMeta.displayName(miniMessage.deserialize(fillerName));
                fillerMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                filler.setItemMeta(fillerMeta);
            }
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

            // Ho tro Nexo item (nexo-id hoac material: "nexo:<id>") va Material thuong
            ItemStack stack = NexoHook.createItemFromSection(itemSec, Material.STONE);
            ItemMeta meta = stack.getItemMeta();

            if (meta != null) {
                String nameRaw = itemSec.getString("name", "<#89dceb><b>Setting Item</b>");
                meta.displayName(miniMessage.deserialize(applyPlaceholders(player, type, nameRaw)));

                List<String> loreRaw = itemSec.getStringList("lore");
                List<Component> loreComp = new ArrayList<>();
                for (String line : loreRaw) {
                    String replaced = applyPlaceholders(player, type, line);
                    for (String splitLine : replaced.split("\n")) {
                        loreComp.add(miniMessage.deserialize(splitLine));
                    }
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
                    int personalSec = plugin.getPearlManager().getPlayerPersonalSeconds(player.getUniqueId());
                    stack.setAmount(Math.max(1, Math.min(64, personalSec)));
                } else if (type.equals("MACE_COOLDOWN")) {
                    int personalSec = plugin.getMaceManager().getPlayerPersonalSeconds(player.getUniqueId());
                    stack.setAmount(Math.max(1, Math.min(64, personalSec)));
                }
            }

            inv.setItem(slot, stack);
            holder.setSlotAction(slot, new MenuItemAction(key, type, itemSec.getString("command", "")));
        }
    }

    public void openAdminSettingMenu(Player admin, int page) {
        String titleRaw = plugin.getConfig().getString(
                "admin-menu.title",
                "<#6c7086>✦ <#f38ba8><b>Admin Cooldowns</b> <#6c7086>✦"
        );
        Component title = miniMessage.deserialize(titleRaw);
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

        for (int i = 0; i <= 8; i++) inv.setItem(i, borderFiller);
        for (int i = 45; i <= 53; i++) inv.setItem(i, borderFiller);
        for (int i = 9; i <= 17; i++) inv.setItem(i, separatorFiller);

        PearlManager pearlManager = plugin.getPearlManager();
        MaceManager maceManager = plugin.getMaceManager();

        boolean pearlGlobalOn = pearlManager.isGlobalForceEnabled();
        int pearlGlobalSec = pearlManager.getGlobalForceSeconds();
        int pearlDefSec = pearlManager.getDefaultSeconds();

        ItemStack globalPearlItem = createSimpleItem(
                Material.ENDER_PEARL,
                "<!italic><#89dceb><b>Global Pearl</b>",
                List.of(
                        "",
                        "<!italic><#a6adc8>Global Force: " + (pearlGlobalOn ? "<#a6e3a1><b>ON</b>" : "<#f38ba8><b>OFF</b>"),
                        "<!italic><#a6adc8>Force Duration: <#f9e2af><b>" + pearlGlobalSec + "s</b>",
                        "<!italic><#a6adc8>Default Minimum: <#89dceb><b>" + pearlDefSec + "s</b>",
                        "",
                        "<!italic><#a6adc8>Presets: " + buildOptionsBar(pearlGlobalSec, pearlManager.getAvailableOptions()),
                        "",
                        "<!italic><white><glyph:left_click></white> <#a6e3a1>Increase <#6c7086>| <white><glyph:right_click></white> <#f9e2af>Decrease",
                        "<!italic><#89dceb>Shift + <white><glyph:left_click></white> <#a6adc8>Toggle Force",
                        "<!italic><#f38ba8>Shift + <white><glyph:right_click></white> <#a6adc8>Set Default (" + pearlGlobalSec + "s)"
                )
        );
        globalPearlItem.setAmount(Math.max(1, Math.min(64, pearlGlobalSec)));
        inv.setItem(2, globalPearlItem);
        holder.setSlotAction(2, new AdminSlotAction("GLOBAL_PEARL", null));

        List<DiscoveredWorld> allWorlds = scanAllWorlds();
        int totalPages = Math.max(1, (int) Math.ceil(allWorlds.size() / 27.0));
        int currentPage = Math.max(0, Math.min(holder.getPage(), totalPages - 1));
        holder.setPage(currentPage);

        ItemStack scannerItem = createSimpleItem(
                Material.RECOVERY_COMPASS,
                "<!italic><#a6e3a1><b>World Scanner</b>",
                List.of(
                        "",
                        "<!italic><#a6adc8>Detected: <#89dceb><b>" + allWorlds.size() + " Worlds</b>",
                        "<!italic><#a6adc8>Page: <#f9e2af><b>" + (currentPage + 1) + "/" + totalPages + "</b>",
                        "",
                        "<!italic><white><glyph:left_click></white> <#a6e3a1>Rescan worlds"
                )
        );
        inv.setItem(4, scannerItem);
        holder.setSlotAction(4, new AdminSlotAction("RESCAN", null));

        boolean maceGlobalOn = maceManager.isGlobalForceEnabled();
        int maceGlobalSec = maceManager.getGlobalForceSeconds();
        int maceDefSec = maceManager.getDefaultSeconds();

        ItemStack globalMaceItem = createSimpleItem(
                Material.MACE,
                "<!italic><#89dceb><b>Global Mace</b>",
                List.of(
                        "",
                        "<!italic><#a6adc8>Global Force: " + (maceGlobalOn ? "<#a6e3a1><b>ON</b>" : "<#f38ba8><b>OFF</b>"),
                        "<!italic><#a6adc8>Force Duration: <#f9e2af><b>" + maceGlobalSec + "s</b>",
                        "<!italic><#a6adc8>Default Minimum: <#89dceb><b>" + maceDefSec + "s</b>",
                        "",
                        "<!italic><#a6adc8>Presets: " + buildOptionsBar(maceGlobalSec, maceManager.getAvailableOptions()),
                        "",
                        "<!italic><white><glyph:left_click></white> <#a6e3a1>Increase <#6c7086>| <white><glyph:right_click></white> <#f9e2af>Decrease",
                        "<!italic><#89dceb>Shift + <white><glyph:left_click></white> <#a6adc8>Toggle Force",
                        "<!italic><#f38ba8>Shift + <white><glyph:right_click></white> <#a6adc8>Set Default (" + maceGlobalSec + "s)"
                )
        );
        globalMaceItem.setAmount(Math.max(1, Math.min(64, maceGlobalSec)));
        inv.setItem(6, globalMaceItem);
        holder.setSlotAction(6, new AdminSlotAction("GLOBAL_MACE", null));

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

            String pearlDisplay = (forcedPearl >= 0) ? "<#a6e3a1><b>" + forcedPearl + "s</b>" : "<#6c7086>OFF";
            String maceDisplay = (forcedMace >= 0) ? "<#a6e3a1><b>" + forcedMace + "s</b>" : "<#6c7086>OFF";

            List<String> wLore = List.of(
                    "<!italic>" + (dw.loaded() ? "<#a6e3a1>● Loaded" : "<#f9e2af>○ Unloaded") + " <#6c7086>• " + dw.source(),
                    "",
                    "<!italic><white><sprite:items:item/ender_pearl></white> <#a6adc8>Pearl: " + pearlDisplay,
                    "<!italic><white><sprite:items:item/mace></white> <#a6adc8>Mace: " + maceDisplay,
                    "",
                    "<!italic><white><glyph:left_click></white> <#89dceb>Cycle Pearl <#6c7086>| <white><glyph:right_click></white> <#f9e2af>Cycle Mace",
                    "<!italic><#f38ba8>Shift + <white><glyph:left_click></white> <#6c7086>Pearl OFF <#6c7086>| <white><glyph:right_click></white> <#6c7086>Mace OFF"
            );

            ItemStack worldItem = createSimpleItem(
                    iconMat,
                    "<!italic>" + (hasAnyForce ? "<#a6e3a1><b>" : "<#89dceb><b>") + wName + "</b>",
                    wLore
            );

            inv.setItem(guiSlot, worldItem);
            holder.setSlotAction(guiSlot, new AdminSlotAction("WORLD_ITEM", wName));
            guiSlot++;
        }

        if (currentPage > 0) {
            ItemStack prevBtn = createSimpleItem(
                    Material.ARROW,
                    "<!italic><#89dceb><b>Previous Page</b>",
                    List.of("<!italic><white><glyph:left_click></white> <#a6adc8>Page " + currentPage)
            );
            inv.setItem(45, prevBtn);
            holder.setSlotAction(45, new AdminSlotAction("PREV_PAGE", null));
        }

        ItemStack playerMenuBtn = createSimpleItem(
                Material.BOOK,
                "<!italic><#89dceb><b>Player Settings</b>",
                List.of("<!italic><white><glyph:left_click></white> <#a6adc8>Back to /setting")
        );
        inv.setItem(48, playerMenuBtn);
        holder.setSlotAction(48, new AdminSlotAction("OPEN_PLAYER_MENU", null));

        ItemStack closeBtn = createSimpleItem(
                Material.BARRIER,
                "<!italic><#f38ba8><b>Close</b>",
                List.of("<!italic><white><glyph:left_click></white> <#6c7086>Close menu")
        );
        inv.setItem(49, closeBtn);
        holder.setSlotAction(49, new AdminSlotAction("CLOSE", null));

        if (currentPage + 1 < totalPages) {
            ItemStack nextBtn = createSimpleItem(
                    Material.ARROW,
                    "<!italic><#89dceb><b>Next Page</b>",
                    List.of("<!italic><white><glyph:left_click></white> <#a6adc8>Page " + (currentPage + 2))
            );
            inv.setItem(53, nextBtn);
            holder.setSlotAction(53, new AdminSlotAction("NEXT_PAGE", null));
        }
    }

    private Material getWorldIconMaterial(DiscoveredWorld dw, boolean hasAnyForce) {
        if (hasAnyForce) return Material.EMERALD_BLOCK;
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

    public List<DiscoveredWorld> scanAllWorlds() {
        Map<String, DiscoveredWorld> map = new LinkedHashMap<>();

        for (World w : Bukkit.getWorlds()) {
            String name = w.getName();
            map.put(name.toLowerCase(), new DiscoveredWorld(name, true, "Bukkit"));
        }

        File pluginsFolder = plugin.getDataFolder().getParentFile();

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
        if (worldName == null || worldName.trim().isEmpty()) return;
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
            if (mv == null || !mv.isEnabled()) return;
            Method getMVWorldManager = mv.getClass().getMethod("getMVWorldManager");
            Object worldManager = getMVWorldManager.invoke(mv);
            if (worldManager == null) return;

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
        if (input == null) return "";

        boolean lobbyBarOn = plugin.getLobbyHotbarManager().isPlayerEnabled(player.getUniqueId());
        String lobbyStatus = lobbyBarOn
                ? plugin.getConfig().getString("lobby-hotbar.messages.status-enabled", "<#a6e3a1><b>ENABLED</b>")
                : plugin.getConfig().getString("lobby-hotbar.messages.status-disabled", "<#f38ba8><b>DISABLED</b>");

        boolean xbowOn = plugin.getXbowManager().isPlayerEnabled(player.getUniqueId());
        String xbowStatus = xbowOn
                ? plugin.getConfig().getString("xbow-counter.messages.status-enabled", "<#a6e3a1><b>ENABLED</b>")
                : plugin.getConfig().getString("xbow-counter.messages.status-disabled", "<#f38ba8><b>DISABLED</b>");

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
                        "<#f38ba8><b>✖ CANNOT CHANGE PERSONAL COOLDOWN</b>\n<!italic><#f9e2af><b>Reason:</b> <#a6adc8>Forced in this world <#89dceb><b>(%forced%s)</b>"
                );
                worldStatus = forcedFormat.replace("%forced%", String.valueOf(effectiveSec))
                                          .replace("%world%", player.getWorld().getName());
            } else if (maceManager.isGlobalForceEnabled()) {
                String globalFormat = plugin.getConfig().getString(
                        "mace-cooldown.messages.world-status-global-forced",
                        "<#f38ba8><b>✖ CANNOT CHANGE PERSONAL COOLDOWN</b>\n<!italic><#f9e2af><b>Reason:</b> <#a6adc8>Forced globally by Admin <#89dceb><b>(%forced%s)</b>"
                );
                worldStatus = globalFormat.replace("%forced%", String.valueOf(effectiveSec));
            } else {
                worldStatus = plugin.getConfig().getString(
                        "mace-cooldown.messages.world-status-normal",
                        "<#a6e3a1>● Personal setting active"
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
                        "<#f38ba8><b>✖ CANNOT CHANGE PERSONAL COOLDOWN</b>\n<!italic><#f9e2af><b>Reason:</b> <#a6adc8>Forced in this world <#89dceb><b>(%forced%s)</b>"
                );
                worldStatus = forcedFormat.replace("%forced%", String.valueOf(effectiveSec))
                                          .replace("%world%", player.getWorld().getName());
            } else if (pearlManager.isGlobalForceEnabled()) {
                String globalFormat = plugin.getConfig().getString(
                        "pearl-cooldown.messages.world-status-global-forced",
                        "<#f38ba8><b>✖ CANNOT CHANGE PERSONAL COOLDOWN</b>\n<!italic><#f9e2af><b>Reason:</b> <#a6adc8>Forced globally by Admin <#89dceb><b>(%forced%s)</b>"
                );
                worldStatus = globalFormat.replace("%forced%", String.valueOf(effectiveSec));
            } else {
                worldStatus = plugin.getConfig().getString(
                        "pearl-cooldown.messages.world-status-normal",
                        "<#a6e3a1>● Personal setting active"
                );
            }
            optionsBar = buildOptionsBar(personalSec, pearlManager.getAvailableOptions());
        }

        String genericStatus = "XBOW_COUNTER_TOGGLE".equalsIgnoreCase(itemType) ? xbowStatus : lobbyStatus;

        return input
                .replace("%player%", player.getName())
                .replace("%world%", player.getWorld().getName())
                .replace("%current%", String.valueOf(personalSec))
                .replace("%default%", String.valueOf(defaultSec))
                .replace("%effective%", String.valueOf(effectiveSec))
                .replace("%world_status%", worldStatus)
                .replace("%options_list%", optionsBar)
                .replace("%lobby_hotbar_status%", lobbyStatus)
                .replace("%xbow_counter_status%", xbowStatus)
                .replace("%status%", genericStatus);
    }

    private String buildOptionsBar(int currentSec, List<Integer> options) {
        if (options.isEmpty()) {
            return "<#89dceb><b>" + currentSec + "s</b>";
        }
        String selectedFormat = plugin.getConfig().getString(
                "setting-menu.options-format.selected",
                "<#a6e3a1><b>[%seconds%s]</b>"
        );
        String unselectedFormat = plugin.getConfig().getString(
                "setting-menu.options-format.unselected",
                "<#6c7086>%seconds%s"
        );
        String separator = plugin.getConfig().getString(
                "setting-menu.options-format.separator",
                " <#6c7086>• "
        );

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < options.size(); i++) {
            int opt = options.get(i);
            if (i > 0) sb.append(separator);
            if (opt == currentSec) {
                sb.append(selectedFormat.replace("%seconds%", String.valueOf(opt)));
            } else {
                sb.append(unselectedFormat.replace("%seconds%", String.valueOf(opt)));
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
