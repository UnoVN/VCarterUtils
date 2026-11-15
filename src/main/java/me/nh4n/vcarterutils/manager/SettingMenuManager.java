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
    private volatile List<DiscoveredWorld> lastScannedWorlds = Collections.emptyList();

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
            } else if (top.getHolder() instanceof WorldDetailMenuHolder detailHolder) {
                populateWorldDetailInventory(top, detailHolder);
            } else if (top.getHolder() instanceof GlobalDetailMenuHolder globalHolder) {
                populateGlobalDetailInventory(top, globalHolder);
            } else if (top.getHolder() instanceof me.nh4n.vcarterutils.manager.TrainingManager.TrainingMenuHolder) {
                plugin.getTrainingManager().refreshTrainingMenu(online);
            }
        }
    }

    public void openSettingMenu(Player player) {
        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("setting-menu.rows", 3)));
        int size = rows * 9;

        String titleRaw = plugin.getConfig().getString(
                "setting-menu.title",
                "<#6c7086>✦ <#89dceb><b>ᴘᴇʀsᴏɴᴀʟ sᴇᴛᴛɪɴɢs</b> <#6c7086>✦"
        );
        Component title = plugin.parseMessage(titleRaw);

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
                fillerMeta.displayName(plugin.parseMessage(fillerName));
                fillerMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                filler.setItemMeta(fillerMeta);
            }
            for (int i = 0; i < inv.getSize(); i++) {
                inv.setItem(i, filler);
            }
        }

        ConfigurationSection itemsSec = plugin.getConfig().getConfigurationSection("setting-menu.items");
        boolean hasTrainingItem = false;

        if (itemsSec != null) {
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
                if (type.equals("TRAINING_MODE_TOGGLE")) {
                    hasTrainingItem = true;
                }
                if (type.equals("ADMIN_MENU") && !player.hasPermission("vcarterutils.admin")) {
                    continue;
                }

                // If this slot is already claimed, shift to next available slot
                if (holder.getSlotAction(slot) != null) {
                    for (int s = 10; s < inv.getSize() - 1; s++) {
                        if (holder.getSlotAction(s) == null) {
                            slot = s;
                            break;
                        }
                    }
                }

                // Supports Nexo items (nexo-id or material: "nexo:<id>") and standard Bukkit Materials
                ItemStack stack = NexoHook.createItemFromSection(itemSec, Material.STONE);
                ItemMeta meta = stack.getItemMeta();

                if (meta != null) {
                    String nameRaw = itemSec.getString("name", "<#89dceb><b>Setting Item</b>");
                    meta.displayName(plugin.parseMessage(applyPlaceholders(player, type, nameRaw)));

                    List<String> loreRaw = itemSec.getStringList("lore");
                    List<Component> loreComp = new ArrayList<>();
                    for (String line : loreRaw) {
                        String replaced = applyPlaceholders(player, type, line);
                        for (String splitLine : replaced.split("\n")) {
                            loreComp.add(plugin.parseMessage(splitLine));
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

        // Guaranteed fallback: If training mode item was not defined in config.yml, render it automatically
        if (!hasTrainingItem) {
            int targetSlot = 13;
            if (targetSlot >= inv.getSize() || holder.getSlotAction(targetSlot) != null) {
                for (int s = 10; s < inv.getSize() - 1; s++) {
                    if (holder.getSlotAction(s) == null) {
                        targetSlot = s;
                        break;
                    }
                }
            }

            if (targetSlot < inv.getSize()) {
                ItemStack stack = new ItemStack(Material.TARGET);
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.displayName(plugin.parseMessage("<!italic><white><sprite:items:item/totem_of_undying></white> <#89dceb><b>Training Mode</b>"));
                    String trainingStatus = plugin.getTrainingManager().isPlayerEnabled(player.getUniqueId())
                            ? "<#a6e3a1><b>✔ ᴇɴᴀʙʟᴇᴅ</b>"
                            : "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ</b>";
                    List<Component> lore = List.of(
                            plugin.parseMessage("<!italic><#6c7086>ᴘʀᴀᴄᴛɪᴄᴇ & sᴀɴᴅʙᴏx"),
                            plugin.parseMessage(""),
                            plugin.parseMessage("<!italic><#6c7086>▪ <#a6adc8>sᴛᴀᴛᴜs: " + trainingStatus),
                            plugin.parseMessage(""),
                            plugin.parseMessage("<!italic><#a6adc8>Infinite totem, blocks, auto-refill,"),
                            plugin.parseMessage("<!italic><#a6adc8>consumables, and crossbow auto-reload."),
                            plugin.parseMessage(""),
                            plugin.parseMessage("<!italic><white><glyph:left_click></white> <#89dceb>ᴏᴘᴛɪᴏɴs <#6c7086>│ <white><glyph:right_click></white> <#a6e3a1>ǫᴜɪᴄᴋ ᴛᴏɢɢʟᴇ")
                    );
                    meta.lore(lore);
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                    stack.setItemMeta(meta);
                }
                inv.setItem(targetSlot, stack);
                holder.setSlotAction(targetSlot, new MenuItemAction("training-mode", "TRAINING_MODE_TOGGLE", ""));
            }
        }
    }

    public void openAdminSettingMenu(Player admin, int page) {
        String titleRaw = plugin.getConfig().getString(
                "admin-menu.title",
                "<#6c7086>✦ <#cba6f7><b>ᴡᴏʀʟᴅ ᴍᴀɴᴀɢᴇʀ</b> <#6c7086>✦"
        );
        Component title = plugin.parseMessage(titleRaw);
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

        // Header border (slots 0-8)
        for (int i = 0; i <= 8; i++) inv.setItem(i, borderFiller);
        // Bottom border (slots 45-53)
        for (int i = 45; i <= 53; i++) inv.setItem(i, borderFiller);

        PearlManager pearlManager = plugin.getPearlManager();
        MaceManager maceManager = plugin.getMaceManager();
        CombatTagManager combatManager = plugin.getCombatTagManager();

        // 1. Slot 0: Global Overrides Quick Button
        boolean pearlGlobalOn = pearlManager.isGlobalForceEnabled();
        int pearlGlobalSec = pearlManager.getGlobalForceSeconds();
        String pearlGlobalStr = pearlGlobalOn ? "<#a6e3a1><b>" + pearlGlobalSec + "s</b>" : "<#6c7086>ᴏꜰꜰ";

        boolean maceGlobalOn = maceManager.isGlobalForceEnabled();
        int maceGlobalSec = maceManager.getGlobalForceSeconds();
        String maceGlobalStr = maceGlobalOn ? "<#a6e3a1><b>" + maceGlobalSec + "s</b>" : "<#6c7086>ᴏꜰꜰ";

        boolean combatGlobalOn = combatManager.isGlobalForceEnabled();
        int combatGlobalSec = combatManager.getGlobalForceSeconds();
        String combatGlobalStr = combatGlobalOn ? "<#a6e3a1><b>" + combatGlobalSec + "s</b>" : "<#6c7086>ᴏꜰꜰ";

        boolean antiGlobalOn = combatManager.isAntiInterruptGlobal();
        String antiGlobalStr = antiGlobalOn ? "<#a6e3a1><b>✔ ᴏɴ</b>" : "<#f38ba8><b>✖ ᴏꜰꜰ</b>";

        ItemStack globalItem = createSimpleItem(
                "nexo:admin_setting",
                Material.NETHER_STAR,
                "<!italic><#cba6f7><b>Global Overrides</b>",
                List.of(
                        "<!italic><#6c7086>sᴇʀᴠᴇʀ-ᴡɪᴅᴇ ʀᴜʟᴇs",
                        "",
                        "<!italic><white><sprite:items:item/ender_pearl></white> <#a6adc8>ᴘᴇᴀʀʟ: " + pearlGlobalStr,
                        "<!italic><white><sprite:items:item/mace></white> <#a6adc8>ᴍᴀᴄᴇ: " + maceGlobalStr,
                        "<!italic><white><sprite:items:item/netherite_sword></white> <#a6adc8>ᴄᴏᴍʙᴀᴛ ᴛᴀɢ: " + combatGlobalStr,
                        "<!italic><white><sprite:items:item/totem_of_undying></white> <#a6adc8>ᴀɴᴛɪ-ɪɴᴛᴇʀʀᴜᴘᴛ: " + antiGlobalStr,
                        "",
                        "<!italic><white><glyph:left_click></white> <#cba6f7>ᴄᴏɴꜰɪɢᴜʀᴇ ɢʟᴏʙᴀʟ"
                )
        );
        inv.setItem(0, globalItem);
        holder.setSlotAction(0, new AdminSlotAction("OPEN_GLOBAL_DETAIL", null));

        // 2. Scan Worlds
        List<DiscoveredWorld> allWorlds = scanAllWorlds();
        int totalPages = Math.max(1, (int) Math.ceil(allWorlds.size() / 36.0));
        int currentPage = Math.max(0, Math.min(holder.getPage(), totalPages - 1));
        holder.setPage(currentPage);

        // 3. Slot 4: World Manager Scanner & Summary
        ItemStack scannerItem = createSimpleItem(
                "nexo:admin_setting",
                Material.RECOVERY_COMPASS,
                "<!italic><#89dceb><b>World Manager</b>",
                List.of(
                        "<!italic><#6c7086>ᴍᴜʟᴛɪ-ᴡᴏʀʟᴅ sᴄᴀɴɴᴇʀ",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>ᴅᴇᴛᴇᴄᴛᴇᴅ: <#89dceb><b>" + allWorlds.size() + " Worlds</b>",
                        "<!italic><#6c7086>▪ <#a6adc8>ᴘᴀɢᴇ: <#f9e2af><b>" + (currentPage + 1) + "/" + totalPages + "</b>",
                        "",
                        "<!italic><#a6adc8>Click any world below to configure",
                        "<!italic><#a6adc8>per-world cooldowns & combat tag.",
                        "",
                        "<!italic><white><glyph:left_click></white> <#a6e3a1>ʀᴇsᴄᴀɴ ᴡᴏʀʟᴅs"
                )
        );
        inv.setItem(4, scannerItem);
        holder.setSlotAction(4, new AdminSlotAction("RESCAN", null));

        // 4. Slot 8: Player Settings Shortcut
        ItemStack playerMenuBtn = createSimpleItem(
                "nexo:settings",
                Material.BOOK,
                "<!italic><#89dceb><b>Settings</b>",
                List.of(
                        "<!italic><#6c7086>ᴘᴇʀsᴏɴᴀʟ ᴍᴇɴᴜ",
                        "",
                        "<!italic><white><glyph:left_click></white> <#a6adc8>ʙᴀᴄᴋ ᴛᴏ /sᴇᴛᴛɪɴɢ"
                )
        );
        inv.setItem(8, playerMenuBtn);
        holder.setSlotAction(8, new AdminSlotAction("OPEN_PLAYER_MENU", null));

        // 5. Populate World Grid (Slots 9 to 44: 36 slots)
        int startIndex = currentPage * 36;
        int endIndex = Math.min(startIndex + 36, allWorlds.size());

        for (int i = startIndex; i < endIndex; i++) {
            int guiSlot = 9 + (i - startIndex);
            DiscoveredWorld dw = allWorlds.get(i);
            String wName = dw.name();

            int forcedPearl = pearlManager.getForcedWorldSeconds(wName);
            int forcedMace = maceManager.getForcedWorldSeconds(wName);
            int forcedCombat = combatManager.getForcedWorldSeconds(wName);
            Boolean worldAnti = combatManager.getWorldAntiInterrupt(wName);
            boolean effectiveAnti = combatManager.isAntiInterruptEnabled(wName);
            boolean hasAnyForce = (forcedPearl >= 0 || forcedMace >= 0 || forcedCombat >= 0 || worldAnti != null);

            Material iconMat = getWorldIconMaterial(dw, hasAnyForce);

            String pearlDisplay = (forcedPearl >= 0) ? "<#a6e3a1><b>" + forcedPearl + "s</b>" : "<#6c7086>ᴏꜰꜰ";
            String maceDisplay = (forcedMace >= 0) ? "<#a6e3a1><b>" + forcedMace + "s</b>" : "<#6c7086>ᴏꜰꜰ";
            String combatDisplay;
            if (forcedCombat == 0) {
                combatDisplay = "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ (0s)</b>";
            } else if (forcedCombat > 0) {
                combatDisplay = "<#a6e3a1><b>" + forcedCombat + "s</b>";
            } else {
                combatDisplay = "<#6c7086>ᴏꜰꜰ (ᴅᴇꜰᴀᴜʟᴛ)";
            }

            String antiDisplay;
            if (worldAnti == null) {
                antiDisplay = "<#6c7086>ᴅᴇꜰᴀᴜʟᴛ (" + (effectiveAnti ? "<#a6e3a1>ᴏɴ" : "<#f38ba8>ᴏꜰꜰ") + "<#6c7086>)";
            } else if (worldAnti) {
                antiDisplay = "<#a6e3a1><b>✔ ᴏɴ</b>";
            } else {
                antiDisplay = "<#f38ba8><b>✖ ᴏꜰꜰ</b>";
            }

            List<String> wLore = List.of(
                    "<!italic>" + (dw.loaded() ? "<#a6e3a1>● ʟᴏᴀᴅᴇᴅ" : "<#f9e2af>○ ᴜɴʟᴏᴀᴅᴇᴅ") + " <#6c7086>• <#a6adc8>" + dw.source(),
                    "",
                    "<!italic><white><sprite:items:item/ender_pearl></white> <#a6adc8>ᴘᴇᴀʀʟ: " + pearlDisplay,
                    "<!italic><white><sprite:items:item/mace></white> <#a6adc8>ᴍᴀᴄᴇ: " + maceDisplay,
                    "<!italic><white><sprite:items:item/netherite_sword></white> <#a6adc8>ᴄᴏᴍʙᴀᴛ ᴛᴀɢ: " + combatDisplay,
                    "<!italic><white><sprite:items:item/totem_of_undying></white> <#a6adc8>ᴀɴᴛɪ-ɪɴᴛᴇʀʀᴜᴘᴛ: " + antiDisplay,
                    "",
                    "<!italic><white><glyph:left_click></white> <#89dceb>ᴄᴏɴꜰɪɢᴜʀᴇ ᴡᴏʀʟᴅ"
            );

            ItemStack worldItem = createSimpleItem(
                    iconMat,
                    "<!italic>" + (hasAnyForce ? "<#a6e3a1><b>● " : "<#89dceb><b>○ ") + wName + "</b>",
                    wLore
            );

            inv.setItem(guiSlot, worldItem);
            holder.setSlotAction(guiSlot, new AdminSlotAction("OPEN_WORLD_DETAIL", wName));
        }

        // 6. Navigation (Slots 45, 49, 53)
        if (currentPage > 0) {
            ItemStack prevBtn = createSimpleItem(
                    "nexo:previous",
                    Material.ARROW,
                    "<!italic><#89dceb><b>« Previous Page</b>",
                    List.of("<!italic><white><glyph:left_click></white> <#a6adc8>ᴘᴀɢᴇ " + currentPage)
            );
            inv.setItem(45, prevBtn);
            holder.setSlotAction(45, new AdminSlotAction("PREV_PAGE", null));
        }

        ItemStack closeBtn = createSimpleItem(
                "nexo:cancel",
                Material.BARRIER,
                "<!italic><#f38ba8><b>✖ Close</b>",
                List.of("<!italic><white><glyph:left_click></white> <#6c7086>ᴄʟᴏsᴇ ᴍᴇɴᴜ")
        );
        inv.setItem(49, closeBtn);
        holder.setSlotAction(49, new AdminSlotAction("CLOSE", null));

        if (currentPage + 1 < totalPages) {
            ItemStack nextBtn = createSimpleItem(
                    "nexo:next",
                    Material.ARROW,
                    "<!italic><#89dceb><b>Next Page »</b>",
                    List.of("<!italic><white><glyph:left_click></white> <#a6adc8>ᴘᴀɢᴇ " + (currentPage + 2))
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
        for (String wName : plugin.getCombatTagManager().getConfiguredForcedWorldNames()) {
            addDiscoveredWorld(map, wName, "Config");
        }

        List<DiscoveredWorld> result = new ArrayList<>(map.values());
        result.sort(Comparator.comparing((DiscoveredWorld d) -> !d.loaded())
                .thenComparing(d -> d.name().toLowerCase()));
        this.lastScannedWorlds = result;
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
        return createSimpleItem(null, material, nameMiniMsg, loreMiniMsg);
    }

    private ItemStack createSimpleItem(String nexoId, Material material, String nameMiniMsg, List<String> loreMiniMsg) {
        ItemStack stack = NexoHook.createItem(nexoId, material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (nameMiniMsg != null) {
                meta.displayName(plugin.parseMessage(nameMiniMsg));
            }
            if (loreMiniMsg != null && !loreMiniMsg.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreMiniMsg) {
                    lore.add(plugin.parseMessage(line));
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
                ? plugin.getConfig().getString("lobby-hotbar.messages.status-enabled", "<#a6e3a1><b>✔ ᴇɴᴀʙʟᴇᴅ</b>")
                : plugin.getConfig().getString("lobby-hotbar.messages.status-disabled", "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ</b>");

        boolean xbowOn = plugin.getXbowManager().isPlayerEnabled(player.getUniqueId());
        String xbowStatus = xbowOn
                ? plugin.getConfig().getString("xbow-counter.messages.status-enabled", "<#a6e3a1><b>✔ ᴇɴᴀʙʟᴇᴅ</b>")
                : plugin.getConfig().getString("xbow-counter.messages.status-disabled", "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ</b>");

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
                        "<#f38ba8><b>✖ CANNOT CHANGE PERSONAL COOLDOWN</b>\n<!italic><#6c7086>▪ <#f9e2af>ʀᴇᴀsᴏɴ: <#a6adc8>Forced in this world <#89dceb><b>(%forced%s)</b>"
                );
                worldStatus = forcedFormat.replace("%forced%", String.valueOf(effectiveSec))
                                          .replace("%world%", player.getWorld().getName());
            } else if (maceManager.isGlobalForceEnabled()) {
                String globalFormat = plugin.getConfig().getString(
                        "mace-cooldown.messages.world-status-global-forced",
                        "<#f38ba8><b>✖ CANNOT CHANGE PERSONAL COOLDOWN</b>\n<!italic><#6c7086>▪ <#f9e2af>ʀᴇᴀsᴏɴ: <#a6adc8>Forced globally by Admin <#89dceb><b>(%forced%s)</b>"
                );
                worldStatus = globalFormat.replace("%forced%", String.valueOf(effectiveSec));
            } else {
                worldStatus = plugin.getConfig().getString(
                        "mace-cooldown.messages.world-status-normal",
                        "<#a6e3a1>● ᴘᴇʀsᴏɴᴀʟ sᴇᴛᴛɪɴɢ ᴀᴄᴛɪᴠᴇ"
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
                        "<#f38ba8><b>✖ CANNOT CHANGE PERSONAL COOLDOWN</b>\n<!italic><#6c7086>▪ <#f9e2af>ʀᴇᴀsᴏɴ: <#a6adc8>Forced in this world <#89dceb><b>(%forced%s)</b>"
                );
                worldStatus = forcedFormat.replace("%forced%", String.valueOf(effectiveSec))
                                          .replace("%world%", player.getWorld().getName());
            } else if (pearlManager.isGlobalForceEnabled()) {
                String globalFormat = plugin.getConfig().getString(
                        "pearl-cooldown.messages.world-status-global-forced",
                        "<#f38ba8><b>✖ CANNOT CHANGE PERSONAL COOLDOWN</b>\n<!italic><#6c7086>▪ <#f9e2af>ʀᴇᴀsᴏɴ: <#a6adc8>Forced globally by Admin <#89dceb><b>(%forced%s)</b>"
                );
                worldStatus = globalFormat.replace("%forced%", String.valueOf(effectiveSec));
            } else {
                worldStatus = plugin.getConfig().getString(
                        "pearl-cooldown.messages.world-status-normal",
                        "<#a6e3a1>● ᴘᴇʀsᴏɴᴀʟ sᴇᴛᴛɪɴɢ ᴀᴄᴛɪᴠᴇ"
                );
            }
            optionsBar = buildOptionsBar(personalSec, pearlManager.getAvailableOptions());
        }

        boolean trainingOn = plugin.getTrainingManager().isPlayerEnabled(player.getUniqueId());
        String trainingStatus = trainingOn
                ? plugin.getConfig().getString("training.messages.status-enabled", "<#a6e3a1><b>✔ ᴇɴᴀʙʟᴇᴅ</b>")
                : plugin.getConfig().getString("training.messages.status-disabled", "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ</b>");

        String genericStatus = lobbyStatus;
        if ("XBOW_COUNTER_TOGGLE".equalsIgnoreCase(itemType)) {
            genericStatus = xbowStatus;
        } else if ("TRAINING_MODE_TOGGLE".equalsIgnoreCase(itemType)) {
            genericStatus = trainingStatus;
        }

        return plugin.formatMessage(input
                .replace("%player%", player.getName())
                .replace("%world%", player.getWorld().getName())
                .replace("%current%", String.valueOf(personalSec))
                .replace("%default%", String.valueOf(defaultSec))
                .replace("%effective%", String.valueOf(effectiveSec))
                .replace("%world_status%", worldStatus)
                .replace("%options_list%", optionsBar)
                .replace("%lobby_hotbar_status%", lobbyStatus)
                .replace("%xbow_counter_status%", xbowStatus)
                .replace("%training_status%", trainingStatus)
                .replace("%training_mode_status%", trainingStatus)
                .replace("%status%", genericStatus));
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

    public void openWorldDetailMenu(Player admin, String worldName) {
        String titleRaw = "<#6c7086>✦ <#cba6f7><b>ᴡᴏʀʟᴅ: <#89dceb>" + worldName + "</b> <#6c7086>✦";
        Component title = plugin.parseMessage(titleRaw);
        WorldDetailMenuHolder holder = new WorldDetailMenuHolder(27, title, worldName);
        populateWorldDetailInventory(holder.getInventory(), holder);
        admin.openInventory(holder.getInventory());
    }

    public void refreshWorldDetailMenu(Player admin) {
        if (admin.getOpenInventory().getTopInventory().getHolder() instanceof WorldDetailMenuHolder holder) {
            populateWorldDetailInventory(holder.getInventory(), holder);
        }
    }

    private void populateWorldDetailInventory(Inventory inv, WorldDetailMenuHolder holder) {
        inv.clear();
        holder.clearSlotActions();

        ItemStack border = createSimpleItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        String worldName = holder.getWorldName();
        PearlManager pearlManager = plugin.getPearlManager();
        MaceManager maceManager = plugin.getMaceManager();
        CombatTagManager combatManager = plugin.getCombatTagManager();

        int pearlSec = pearlManager.getForcedWorldSeconds(worldName);
        int maceSec = maceManager.getForcedWorldSeconds(worldName);
        int combatSec = combatManager.getForcedWorldSeconds(worldName);
        Boolean worldAnti = combatManager.getWorldAntiInterrupt(worldName);
        boolean hasAnyForce = (pearlSec >= 0 || maceSec >= 0 || combatSec >= 0 || worldAnti != null);

        // Slot 4: World Header
        DiscoveredWorld dw = findDiscoveredWorld(worldName);
        Material worldIcon = (dw != null) ? getWorldIconMaterial(dw, hasAnyForce) : Material.GRASS_BLOCK;
        String loadedStr = (dw != null && dw.loaded()) ? "<#a6e3a1>● ʟᴏᴀᴅᴇᴅ" : "<#f9e2af>○ ᴜɴʟᴏᴀᴅᴇᴅ";
        String sourceStr = (dw != null) ? dw.source() : "Bukkit";

        ItemStack worldHeader = createSimpleItem(
                worldIcon,
                "<!italic><#89dceb><b>" + worldName + "</b>",
                List.of(
                        "<!italic>" + loadedStr + " <#6c7086>• <#a6adc8>" + sourceStr,
                        "",
                        "<!italic><#a6adc8>Configure per-world cooldown overrides and",
                        "<!italic><#a6adc8>combat tag duration for this world."
                )
        );
        inv.setItem(4, worldHeader);

        // Slot 10: Pearl Cooldown
        String pearlStatus = (pearlSec >= 0) ? "<#a6e3a1><b>" + pearlSec + "s</b>" : "<#6c7086>ᴏꜰꜰ (ᴅᴇꜰᴀᴜʟᴛ: " + pearlManager.getDefaultSeconds() + "s)";
        ItemStack pearlItem = createSimpleItem(
                Material.ENDER_PEARL,
                "<!italic><white><sprite:items:item/ender_pearl></white> <#89dceb><b>Pearl Cooldown</b>",
                List.of(
                        "<!italic><#6c7086>ᴡᴏʀʟᴅ ᴏᴠᴇʀʀɪᴅᴇ",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>sᴛᴀᴛᴜs: " + pearlStatus,
                        "<!italic><#6c7086>▪ <#a6adc8>ᴍɪɴ ᴅᴇꜰᴀᴜʟᴛ: <#89dceb><b>" + pearlManager.getDefaultSeconds() + "s</b>",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>ᴘʀᴇsᴇᴛs: " + buildOptionsBar(pearlSec, pearlManager.getAvailableOptions()),
                        "",
                        "<!italic><white><glyph:left_click></white> <#a6e3a1>ɴᴇxᴛ <#6c7086>│ <white><glyph:right_click></white> <#f9e2af>ᴘʀᴇᴠɪᴏᴜs",
                        "<!italic><#f38ba8>sʜɪꜰᴛ + <white><glyph:left_click></white> <#6c7086>ᴛᴜʀɴ ᴏꜰꜰ (ᴅᴇꜰᴀᴜʟᴛ)"
                )
        );
        pearlItem.setAmount(Math.max(1, Math.min(64, pearlSec > 0 ? pearlSec : 1)));
        inv.setItem(10, pearlItem);
        holder.setSlotAction(10, new AdminSlotAction("DETAIL_PEARL", worldName));

        // Slot 12: Mace Cooldown
        String maceStatus = (maceSec >= 0) ? "<#a6e3a1><b>" + maceSec + "s</b>" : "<#6c7086>ᴏꜰꜰ (ᴅᴇꜰᴀᴜʟᴛ: " + maceManager.getDefaultSeconds() + "s)";
        ItemStack maceItem = createSimpleItem(
                Material.MACE,
                "<!italic><white><sprite:items:item/mace></white> <#89dceb><b>Mace Cooldown</b>",
                List.of(
                        "<!italic><#6c7086>ᴡᴏʀʟᴅ ᴏᴠᴇʀʀɪᴅᴇ",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>sᴛᴀᴛᴜs: " + maceStatus,
                        "<!italic><#6c7086>▪ <#a6adc8>ᴍɪɴ ᴅᴇꜰᴀᴜʟᴛ: <#89dceb><b>" + maceManager.getDefaultSeconds() + "s</b>",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>ᴘʀᴇsᴇᴛs: " + buildOptionsBar(maceSec, maceManager.getAvailableOptions()),
                        "",
                        "<!italic><white><glyph:left_click></white> <#a6e3a1>ɴᴇxᴛ <#6c7086>│ <white><glyph:right_click></white> <#f9e2af>ᴘʀᴇᴠɪᴏᴜs",
                        "<!italic><#f38ba8>sʜɪꜰᴛ + <white><glyph:left_click></white> <#6c7086>ᴛᴜʀɴ ᴏꜰꜰ (ᴅᴇꜰᴀᴜʟᴛ)"
                )
        );
        maceItem.setAmount(Math.max(1, Math.min(64, maceSec > 0 ? maceSec : 1)));
        inv.setItem(12, maceItem);
        holder.setSlotAction(12, new AdminSlotAction("DETAIL_MACE", worldName));

        // Slot 14: Combat Tag Timer
        String combatStatus;
        if (combatSec == 0) {
            combatStatus = "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ (0s)</b>";
        } else if (combatSec > 0) {
            combatStatus = "<#a6e3a1><b>" + combatSec + "s</b>";
        } else {
            combatStatus = "<#6c7086>ᴏꜰꜰ (ᴅᴇꜰᴀᴜʟᴛ: " + combatManager.getDefaultSeconds() + "s)";
        }
        ItemStack combatItem = createSimpleItem(
                Material.NETHERITE_SWORD,
                "<!italic><white><sprite:items:item/netherite_sword></white> <#89dceb><b>Combat Tag Timer</b>",
                List.of(
                        "<!italic><#6c7086>ᴡᴏʀʟᴅ ᴏᴠᴇʀʀɪᴅᴇ",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>sᴛᴀᴛᴜs: " + combatStatus,
                        "<!italic><#6c7086>▪ <#a6adc8>ᴍɪɴ ᴅᴇꜰᴀᴜʟᴛ: <#89dceb><b>" + combatManager.getDefaultSeconds() + "s</b>",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>ᴘʀᴇsᴇᴛs: " + buildOptionsBar(combatSec, combatManager.getAvailableOptions()),
                        "",
                        "<!italic><white><glyph:left_click></white> <#a6e3a1>ɴᴇxᴛ <#6c7086>│ <white><glyph:right_click></white> <#f9e2af>ᴘʀᴇᴠɪᴏᴜs",
                        "<!italic><#f38ba8>sʜɪꜰᴛ + <white><glyph:left_click></white> <#f38ba8>ᴅɪsᴀʙʟᴇ ᴄᴏᴍʙᴀᴛ (0s)",
                        "<!italic><#f38ba8>sʜɪꜰᴛ + <white><glyph:right_click></white> <#6c7086>ᴛᴜʀɴ ᴏꜰꜰ (ᴅᴇꜰᴀᴜʟᴛ)"
                )
        );
        combatItem.setAmount(Math.max(1, Math.min(64, combatSec > 0 ? combatSec : 1)));
        inv.setItem(14, combatItem);
        holder.setSlotAction(14, new AdminSlotAction("DETAIL_COMBAT", worldName));

        // Slot 16: Anti-Interrupt Protection
        String antiStatus;
        if (worldAnti == null) {
            antiStatus = "<#6c7086>ᴅᴇꜰᴀᴜʟᴛ (" + (combatManager.isAntiInterruptGlobal() ? "<#a6e3a1>ᴏɴ" : "<#f38ba8>ᴏꜰꜰ") + "<#6c7086>)";
        } else if (worldAnti) {
            antiStatus = "<#a6e3a1><b>✔ ᴇɴᴀʙʟᴇᴅ</b>";
        } else {
            antiStatus = "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ</b>";
        }

        ItemStack antiItem = createSimpleItem(
                Material.SHIELD,
                "<!italic><white><sprite:items:item/totem_of_undying></white> <#89dceb><b>Anti-Interrupt Protection</b>",
                List.of(
                        "<!italic><#6c7086>ᴡᴏʀʟᴅ ᴏᴠᴇʀʀɪᴅᴇ",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>sᴛᴀᴛᴜs: " + antiStatus,
                        "",
                        "<!italic><#a6adc8>Protects active 1v1 fights from",
                        "<!italic><#a6adc8>third-party damage and interference.",
                        "",
                        "<!italic><white><glyph:left_click></white> <#a6e3a1>ᴛᴏɢɢʟᴇ ᴏɴ/ᴏꜰꜰ",
                        "<!italic><#f38ba8>sʜɪꜰᴛ + <white><glyph:left_click></white> <#6c7086>ʀᴇsᴇᴛ ᴛᴏ ᴅᴇꜰᴀᴜʟᴛ"
                )
        );
        inv.setItem(16, antiItem);
        holder.setSlotAction(16, new AdminSlotAction("DETAIL_INTERRUPT", worldName));

        // Slot 22: Back Button
        ItemStack backBtn = createSimpleItem(
                "nexo:cancel",
                Material.ARROW,
                "<!italic><#f38ba8><b>« Back to World Manager</b>",
                List.of("<!italic><white><glyph:left_click></white> <#a6adc8>ʀᴇᴛᴜʀɴ ᴛᴏ ᴡᴏʀʟᴅ ʟɪsᴛ")
        );
        inv.setItem(22, backBtn);
        holder.setSlotAction(22, new AdminSlotAction("DETAIL_BACK", worldName));
    }

    private DiscoveredWorld findDiscoveredWorld(String worldName) {
        if (worldName == null) return null;
        List<DiscoveredWorld> list = this.lastScannedWorlds;
        if (list.isEmpty()) {
            list = scanAllWorlds();
        }
        for (DiscoveredWorld dw : list) {
            if (dw.name().equalsIgnoreCase(worldName)) {
                return dw;
            }
        }
        return null;
    }

    public void openGlobalDetailMenu(Player admin) {
        String titleRaw = "<#6c7086>✦ <#cba6f7><b>ɢʟᴏʙᴀʟ ᴏᴠᴇʀʀɪᴅᴇs</b> <#6c7086>✦";
        Component title = plugin.parseMessage(titleRaw);
        GlobalDetailMenuHolder holder = new GlobalDetailMenuHolder(27, title);
        populateGlobalDetailInventory(holder.getInventory(), holder);
        admin.openInventory(holder.getInventory());
    }

    public void refreshGlobalDetailMenu(Player admin) {
        if (admin.getOpenInventory().getTopInventory().getHolder() instanceof GlobalDetailMenuHolder holder) {
            populateGlobalDetailInventory(holder.getInventory(), holder);
        }
    }

    private void populateGlobalDetailInventory(Inventory inv, GlobalDetailMenuHolder holder) {
        inv.clear();
        holder.clearSlotActions();

        ItemStack border = createSimpleItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        PearlManager pearlManager = plugin.getPearlManager();
        MaceManager maceManager = plugin.getMaceManager();
        CombatTagManager combatManager = plugin.getCombatTagManager();

        // Slot 4: Header
        ItemStack header = createSimpleItem(
                Material.NETHER_STAR,
                "<!italic><#cba6f7><b>Global Overrides</b>",
                List.of(
                        "<!italic><#6c7086>sᴇʀᴠᴇʀ-ᴡɪᴅᴇ ʀᴜʟᴇs",
                        "",
                        "<!italic><#a6adc8>Configure server-wide forced cooldowns.",
                        "<!italic><#a6adc8>Applied to worlds without custom overrides."
                )
        );
        inv.setItem(4, header);

        // Slot 10: Global Pearl
        boolean pearlGlobalOn = pearlManager.isGlobalForceEnabled();
        int pearlGlobalSec = pearlManager.getGlobalForceSeconds();
        int pearlDefSec = pearlManager.getDefaultSeconds();
        ItemStack globalPearlItem = createSimpleItem(
                Material.ENDER_PEARL,
                "<!italic><white><sprite:items:item/ender_pearl></white> <#89dceb><b>Global Pearl</b>",
                List.of(
                        "<!italic><#6c7086>ɢʟᴏʙᴀʟ ᴏᴠᴇʀʀɪᴅᴇ",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>ɢʟᴏʙᴀʟ ꜰᴏʀᴄᴇ: " + (pearlGlobalOn ? "<#a6e3a1><b>✔ ᴏɴ</b>" : "<#f38ba8><b>✖ ᴏꜰꜰ</b>"),
                        "<!italic><#6c7086>▪ <#a6adc8>ꜰᴏʀᴄᴇ ᴅᴜʀᴀᴛɪᴏɴ: <#f9e2af><b>" + pearlGlobalSec + "s</b>",
                        "<!italic><#6c7086>▪ <#a6adc8>ᴍɪɴ ᴅᴇꜰᴀᴜʟᴛ: <#89dceb><b>" + pearlDefSec + "s</b>",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>ᴘʀᴇsᴇᴛs: " + buildOptionsBar(pearlGlobalSec, pearlManager.getAvailableOptions()),
                        "",
                        "<!italic><white><glyph:left_click></white> <#a6e3a1>ɴᴇxᴛ <#6c7086>│ <white><glyph:right_click></white> <#f9e2af>ᴘʀᴇᴠɪᴏᴜs",
                        "<!italic><#89dceb>sʜɪꜰᴛ + <white><glyph:left_click></white> <#a6adc8>ᴛᴏɢɢʟᴇ ɢʟᴏʙᴀʟ ꜰᴏʀᴄᴇ",
                        "<!italic><#f38ba8>sʜɪꜰᴛ + <white><glyph:right_click></white> <#a6adc8>sᴇᴛ ᴅᴇꜰᴀᴜʟᴛ (" + pearlGlobalSec + "s)"
                )
        );
        globalPearlItem.setAmount(Math.max(1, Math.min(64, pearlGlobalSec)));
        inv.setItem(10, globalPearlItem);
        holder.setSlotAction(10, new AdminSlotAction("GLOBAL_PEARL", null));

        // Slot 12: Global Mace
        boolean maceGlobalOn = maceManager.isGlobalForceEnabled();
        int maceGlobalSec = maceManager.getGlobalForceSeconds();
        int maceDefSec = maceManager.getDefaultSeconds();
        ItemStack globalMaceItem = createSimpleItem(
                Material.MACE,
                "<!italic><white><sprite:items:item/mace></white> <#89dceb><b>Global Mace</b>",
                List.of(
                        "<!italic><#6c7086>ɢʟᴏʙᴀʟ ᴏᴠᴇʀʀɪᴅᴇ",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>ɢʟᴏʙᴀʟ ꜰᴏʀᴄᴇ: " + (maceGlobalOn ? "<#a6e3a1><b>✔ ᴏɴ</b>" : "<#f38ba8><b>✖ ᴏꜰꜰ</b>"),
                        "<!italic><#6c7086>▪ <#a6adc8>ꜰᴏʀᴄᴇ ᴅᴜʀᴀᴛɪᴏɴ: <#f9e2af><b>" + maceGlobalSec + "s</b>",
                        "<!italic><#6c7086>▪ <#a6adc8>ᴍɪɴ ᴅᴇꜰᴀᴜʟᴛ: <#89dceb><b>" + maceDefSec + "s</b>",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>ᴘʀᴇsᴇᴛs: " + buildOptionsBar(maceGlobalSec, maceManager.getAvailableOptions()),
                        "",
                        "<!italic><white><glyph:left_click></white> <#a6e3a1>ɴᴇxᴛ <#6c7086>│ <white><glyph:right_click></white> <#f9e2af>ᴘʀᴇᴠɪᴏᴜs",
                        "<!italic><#89dceb>sʜɪꜰᴛ + <white><glyph:left_click></white> <#a6adc8>ᴛᴏɢɢʟᴇ ɢʟᴏʙᴀʟ ꜰᴏʀᴄᴇ",
                        "<!italic><#f38ba8>sʜɪꜰᴛ + <white><glyph:right_click></white> <#a6adc8>sᴇᴛ ᴅᴇꜰᴀᴜʟᴛ (" + maceGlobalSec + "s)"
                )
        );
        globalMaceItem.setAmount(Math.max(1, Math.min(64, maceGlobalSec)));
        inv.setItem(12, globalMaceItem);
        holder.setSlotAction(12, new AdminSlotAction("GLOBAL_MACE", null));

        // Slot 14: Global Combat Tag
        boolean combatGlobalOn = combatManager.isGlobalForceEnabled();
        int combatGlobalSec = combatManager.getGlobalForceSeconds();
        int combatDefSec = combatManager.getDefaultSeconds();
        ItemStack globalCombatItem = createSimpleItem(
                Material.NETHERITE_SWORD,
                "<!italic><white><sprite:items:item/netherite_sword></white> <#89dceb><b>Global Combat Tag</b>",
                List.of(
                        "<!italic><#6c7086>ɢʟᴏʙᴀʟ ᴏᴠᴇʀʀɪᴅᴇ",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>ɢʟᴏʙᴀʟ ꜰᴏʀᴄᴇ: " + (combatGlobalOn ? "<#a6e3a1><b>✔ ᴏɴ</b>" : "<#f38ba8><b>✖ ᴏꜰꜰ</b>"),
                        "<!italic><#6c7086>▪ <#a6adc8>ꜰᴏʀᴄᴇ ᴅᴜʀᴀᴛɪᴏɴ: <#f9e2af><b>" + combatGlobalSec + "s</b>",
                        "<!italic><#6c7086>▪ <#a6adc8>ᴍɪɴ ᴅᴇꜰᴀᴜʟᴛ: <#89dceb><b>" + combatDefSec + "s</b>",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>ᴘʀᴇsᴇᴛs: " + buildOptionsBar(combatGlobalSec, combatManager.getAvailableOptions()),
                        "",
                        "<!italic><white><glyph:left_click></white> <#a6e3a1>ɴᴇxᴛ <#6c7086>│ <white><glyph:right_click></white> <#f9e2af>ᴘʀᴇᴠɪᴏᴜs",
                        "<!italic><#89dceb>sʜɪꜰᴛ + <white><glyph:left_click></white> <#a6adc8>ᴛᴏɢɢʟᴇ ɢʟᴏʙᴀʟ ꜰᴏʀᴄᴇ",
                        "<!italic><#f38ba8>sʜɪꜰᴛ + <white><glyph:right_click></white> <#a6adc8>sᴇᴛ ᴅᴇꜰᴀᴜʟᴛ (" + combatGlobalSec + "s)"
                )
        );
        globalCombatItem.setAmount(Math.max(1, Math.min(64, combatGlobalSec)));
        inv.setItem(14, globalCombatItem);
        holder.setSlotAction(14, new AdminSlotAction("GLOBAL_COMBAT", null));

        // Slot 16: Global Anti-Interrupt
        boolean globalAntiOn = combatManager.isAntiInterruptGlobal();
        String globalAntiStatus = globalAntiOn ? "<#a6e3a1><b>✔ ᴇɴᴀʙʟᴇᴅ</b>" : "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ</b>";
        ItemStack globalAntiItem = createSimpleItem(
                Material.SHIELD,
                "<!italic><white><sprite:items:item/totem_of_undying></white> <#89dceb><b>Global Anti-Interrupt</b>",
                List.of(
                        "<!italic><#6c7086>ɢʟᴏʙᴀʟ ᴏᴠᴇʀʀɪᴅᴇ",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>ɢʟᴏʙᴀʟ sᴛᴀᴛᴜs: " + globalAntiStatus,
                        "",
                        "<!italic><#a6adc8>Default anti-interrupt rule for all",
                        "<!italic><#a6adc8>worlds without custom overrides.",
                        "",
                        "<!italic><white><glyph:left_click></white> <#a6e3a1>ᴛᴏɢɢʟᴇ ᴏɴ/ᴏꜰꜰ"
                )
        );
        inv.setItem(16, globalAntiItem);
        holder.setSlotAction(16, new AdminSlotAction("GLOBAL_INTERRUPT", null));

        // Slot 22: Back Button
        ItemStack backBtn = createSimpleItem(
                "nexo:cancel",
                Material.ARROW,
                "<!italic><#f38ba8><b>« Back to World Manager</b>",
                List.of("<!italic><white><glyph:left_click></white> <#a6adc8>ʀᴇᴛᴜʀɴ ᴛᴏ ᴡᴏʀʟᴅ ʟɪsᴛ")
        );
        inv.setItem(22, backBtn);
        holder.setSlotAction(22, new AdminSlotAction("GLOBAL_BACK", null));
    }

    public static class GlobalDetailMenuHolder implements InventoryHolder {
        private final Inventory inventory;
        private final Map<Integer, AdminSlotAction> slotActions = new HashMap<>();

        public GlobalDetailMenuHolder(int size, Component title) {
            this.inventory = Bukkit.createInventory(this, size, title);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
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

    public static class WorldDetailMenuHolder implements InventoryHolder {
        private final Inventory inventory;
        private final String worldName;
        private final Map<Integer, AdminSlotAction> slotActions = new HashMap<>();

        public WorldDetailMenuHolder(int size, Component title, String worldName) {
            this.inventory = Bukkit.createInventory(this, size, title);
            this.worldName = worldName;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public String getWorldName() {
            return worldName;
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
