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

        int size = inv.getSize();
        boolean fillerEnabled = plugin.getConfig().getBoolean("setting-menu.filler.enabled", true);
        boolean borderEnabled = plugin.getConfig().getBoolean("setting-menu.border.enabled", true);

        ItemStack fillerItem = null;
        if (fillerEnabled) {
            ConfigurationSection fillerSec = plugin.getConfig().getConfigurationSection("setting-menu.filler");
            fillerItem = NexoHook.createItemFromSection(fillerSec, Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta fillerMeta = fillerItem.getItemMeta();
            if (fillerMeta != null) {
                String fillerName = plugin.getConfig().getString("setting-menu.filler.name", " ");
                fillerMeta.displayName(plugin.parseMessage(fillerName));
                fillerMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                fillerItem.setItemMeta(fillerMeta);
            }
        }

        ItemStack borderItem = null;
        if (borderEnabled && size >= 27) {
            ConfigurationSection borderSec = plugin.getConfig().getConfigurationSection("setting-menu.border");
            borderItem = NexoHook.createItemFromSection(borderSec, Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta borderMeta = borderItem.getItemMeta();
            if (borderMeta != null) {
                String borderName = plugin.getConfig().getString("setting-menu.border.name", " ");
                borderMeta.displayName(plugin.parseMessage(borderName));
                borderMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                borderItem.setItemMeta(borderMeta);
            }
        }

        for (int i = 0; i < size; i++) {
            boolean isEdgeRow = size >= 27 && (i < 9 || i >= size - 9);
            if (isEdgeRow && borderItem != null) {
                inv.setItem(i, borderItem);
            } else if (fillerItem != null) {
                inv.setItem(i, fillerItem);
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
                    String trainingStatus = formatBooleanStatus(plugin.getTrainingManager().isPlayerEnabled(player.getUniqueId()));
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

        applyMenuFrame(inv, false);

        PearlManager pearlManager = plugin.getPearlManager();
        MaceManager maceManager = plugin.getMaceManager();
        CombatTagManager combatManager = plugin.getCombatTagManager();

        // 1. Slot 0: Global Overrides Quick Button
        String pearlGlobalStr = formatGlobalTimerStatus(pearlManager.isGlobalForceEnabled(), pearlManager.getGlobalForceSeconds());
        String maceGlobalStr = formatGlobalTimerStatus(maceManager.isGlobalForceEnabled(), maceManager.getGlobalForceSeconds());
        String combatGlobalStr = formatGlobalTimerStatus(combatManager.isGlobalForceEnabled(), combatManager.getGlobalForceSeconds());
        String antiGlobalStr = formatBooleanStatus(combatManager.isAntiInterruptGlobal());

        ItemStack globalItem = createSimpleItem(
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
                "<!italic><#cba6f7><b>World Manager</b>",
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

            String pearlDisplay = formatWorldGridTimer(forcedPearl, false);
            String maceDisplay = formatWorldGridTimer(forcedMace, false);
            String combatDisplay = formatWorldGridTimer(forcedCombat, true);

            String antiDisplay;
            if (worldAnti == null) {
                antiDisplay = "<#6c7086>○ ᴅᴇꜰᴀᴜʟᴛ (" + (effectiveAnti ? "<#a6e3a1>ᴏɴ" : "<#f38ba8>ᴏꜰꜰ") + "<#6c7086>)";
            } else {
                antiDisplay = formatBooleanStatus(worldAnti);
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
                List.of("<!italic><white><glyph:left_click></white> <#a6adc8>ᴄʟᴏsᴇ ᴍᴇɴᴜ")
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

    private void applyMenuFrame(Inventory inv, boolean fillInner) {
        ItemStack border = createSimpleItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        ItemStack inner = fillInner ? createSimpleItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()) : null;
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            boolean isEdgeRow = (i < 9 || i >= size - 9);
            if (isEdgeRow) {
                inv.setItem(i, border);
            } else if (inner != null) {
                inv.setItem(i, inner);
            }
        }
    }

    private String formatBooleanStatus(boolean enabled) {
        return enabled ? "<#a6e3a1><b>✔ ᴇɴᴀʙʟᴇᴅ</b>" : "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ</b>";
    }

    private String formatGlobalTimerStatus(boolean enabled, int seconds) {
        return enabled ? "<#a6e3a1><b>✔ " + seconds + "s</b>" : "<#6c7086>○ ᴏꜰꜰ";
    }

    private String formatWorldGridTimer(int seconds, boolean allowZeroDisable) {
        if (allowZeroDisable && seconds == 0) {
            return "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ (0s)</b>";
        }
        return (seconds >= 0) ? "<#a6e3a1><b>✔ " + seconds + "s</b>" : "<#6c7086>○ ᴅᴇꜰᴀᴜʟᴛ";
    }

    private String formatDetailTimerStatus(int seconds, int defaultSeconds, boolean allowZeroDisable) {
        if (allowZeroDisable && seconds == 0) {
            return "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ (0s)</b>";
        }
        if (seconds >= 0) {
            return "<#a6e3a1><b>✔ " + seconds + "s</b>";
        }
        return "<#6c7086>○ ᴅᴇꜰᴀᴜʟᴛ (" + defaultSeconds + "s)";
    }

    private ItemStack createBackButton(String title, String subtitle) {
        return createSimpleItem(
                "nexo:previous",
                Material.ARROW,
                "<!italic><#89dceb><b>« " + title + "</b>",
                List.of("<!italic><white><glyph:left_click></white> <#a6adc8>" + subtitle)
        );
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
        String titleRaw = "<#6c7086>✦ <#cba6f7><b>ᴡᴏʀʟᴅ ʀᴜʟᴇs</b> <#6c7086>▪ <#89dceb><b>" + worldName + "</b> <#6c7086>✦";
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

        applyMenuFrame(inv, true);

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
        inv.setItem(10, createWorldTimerItem(
                Material.ENDER_PEARL, "ender_pearl", "Pearl Cooldown",
                pearlSec, pearlManager.getDefaultSeconds(), pearlManager.getAvailableOptions(), false
        ));
        holder.setSlotAction(10, new AdminSlotAction("DETAIL_PEARL", worldName));

        // Slot 12: Mace Cooldown
        inv.setItem(12, createWorldTimerItem(
                Material.MACE, "mace", "Mace Cooldown",
                maceSec, maceManager.getDefaultSeconds(), maceManager.getAvailableOptions(), false
        ));
        holder.setSlotAction(12, new AdminSlotAction("DETAIL_MACE", worldName));

        // Slot 14: Combat Tag Timer
        inv.setItem(14, createWorldTimerItem(
                Material.NETHERITE_SWORD, "netherite_sword", "Combat Tag Timer",
                combatSec, combatManager.getDefaultSeconds(), combatManager.getAvailableOptions(), true
        ));
        holder.setSlotAction(14, new AdminSlotAction("DETAIL_COMBAT", worldName));

        // Slot 16: Anti-Interrupt Protection
        String antiStatus = (worldAnti == null)
                ? "<#6c7086>○ ᴅᴇꜰᴀᴜʟᴛ (" + (combatManager.isAntiInterruptGlobal() ? "<#a6e3a1>ᴏɴ" : "<#f38ba8>ᴏꜰꜰ") + "<#6c7086>)"
                : formatBooleanStatus(worldAnti);

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
                        "<!italic><white><glyph:left_click></white> <#89dceb>ᴛᴏɢɢʟᴇ ᴏɴ/ᴏꜰꜰ",
                        "<!italic><#f38ba8>sʜɪꜰᴛ + <white><glyph:left_click></white> <#a6adc8>ʀᴇsᴇᴛ ᴅᴇꜰᴀᴜʟᴛ"
                )
        );
        inv.setItem(16, antiItem);
        holder.setSlotAction(16, new AdminSlotAction("DETAIL_INTERRUPT", worldName));

        // Slot 22: Back Button
        inv.setItem(22, createBackButton("Back to World Manager", "ʀᴇᴛᴜʀɴ ᴛᴏ ᴡᴏʀʟᴅ ʟɪsᴛ"));
        holder.setSlotAction(22, new AdminSlotAction("DETAIL_BACK", worldName));
    }

    private ItemStack createWorldTimerItem(
            Material mat,
            String spriteName,
            String title,
            int seconds,
            int defaultSeconds,
            List<Integer> options,
            boolean allowZeroDisable
    ) {
        List<String> lore = new ArrayList<>();
        lore.add("<!italic><#6c7086>ᴡᴏʀʟᴅ ᴏᴠᴇʀʀɪᴅᴇ");
        lore.add("");
        lore.add("<!italic><#6c7086>▪ <#a6adc8>sᴛᴀᴛᴜs: " + formatDetailTimerStatus(seconds, defaultSeconds, allowZeroDisable));
        lore.add("<!italic><#6c7086>▪ <#a6adc8>ᴍɪɴ ᴅᴇꜰᴀᴜʟᴛ: <#89dceb><b>" + defaultSeconds + "s</b>");
        lore.add("");
        lore.add("<!italic><#6c7086>▪ <#a6adc8>ᴘʀᴇsᴇᴛs: " + buildOptionsBar(seconds, options));
        lore.add("");
        lore.add("<!italic><white><glyph:left_click></white> <#a6e3a1>ɴᴇxᴛ <#6c7086>│ <white><glyph:right_click></white> <#f9e2af>ᴘʀᴇᴠɪᴏᴜs");
        if (allowZeroDisable) {
            lore.add("<!italic><#f38ba8>sʜɪꜰᴛ + <white><glyph:left_click></white> <#a6adc8>ᴅɪsᴀʙʟᴇ ᴄᴏᴍʙᴀᴛ (0s)");
            lore.add("<!italic><#f38ba8>sʜɪꜰᴛ + <white><glyph:right_click></white> <#a6adc8>ʀᴇsᴇᴛ ᴅᴇꜰᴀᴜʟᴛ");
        } else {
            lore.add("<!italic><#f38ba8>sʜɪꜰᴛ + <white><glyph:left_click></white> <#a6adc8>ʀᴇsᴇᴛ ᴅᴇꜰᴀᴜʟᴛ");
        }

        ItemStack item = createSimpleItem(
                mat,
                "<!italic><white><sprite:items:item/" + spriteName + "></white> <#89dceb><b>" + title + "</b>",
                lore
        );
        item.setAmount(Math.max(1, Math.min(64, seconds > 0 ? seconds : 1)));
        return item;
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

        applyMenuFrame(inv, true);

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
        inv.setItem(10, createGlobalTimerItem(
                Material.ENDER_PEARL, "ender_pearl", "Global Pearl",
                pearlManager.isGlobalForceEnabled(), pearlManager.getGlobalForceSeconds(),
                pearlManager.getDefaultSeconds(), pearlManager.getAvailableOptions()
        ));
        holder.setSlotAction(10, new AdminSlotAction("GLOBAL_PEARL", null));

        // Slot 12: Global Mace
        inv.setItem(12, createGlobalTimerItem(
                Material.MACE, "mace", "Global Mace",
                maceManager.isGlobalForceEnabled(), maceManager.getGlobalForceSeconds(),
                maceManager.getDefaultSeconds(), maceManager.getAvailableOptions()
        ));
        holder.setSlotAction(12, new AdminSlotAction("GLOBAL_MACE", null));

        // Slot 14: Global Combat Tag
        inv.setItem(14, createGlobalTimerItem(
                Material.NETHERITE_SWORD, "netherite_sword", "Global Combat Tag",
                combatManager.isGlobalForceEnabled(), combatManager.getGlobalForceSeconds(),
                combatManager.getDefaultSeconds(), combatManager.getAvailableOptions()
        ));
        holder.setSlotAction(14, new AdminSlotAction("GLOBAL_COMBAT", null));

        // Slot 16: Global Anti-Interrupt
        String globalAntiStatus = formatBooleanStatus(combatManager.isAntiInterruptGlobal());
        ItemStack globalAntiItem = createSimpleItem(
                Material.SHIELD,
                "<!italic><white><sprite:items:item/totem_of_undying></white> <#89dceb><b>Global Anti-Interrupt</b>",
                List.of(
                        "<!italic><#6c7086>ɢʟᴏʙᴀʟ ᴏᴠᴇʀʀɪᴅᴇ",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>sᴛᴀᴛᴜs: " + globalAntiStatus,
                        "",
                        "<!italic><#a6adc8>Default anti-interrupt rule for all",
                        "<!italic><#a6adc8>worlds without custom overrides.",
                        "",
                        "<!italic><white><glyph:left_click></white> <#89dceb>ᴛᴏɢɢʟᴇ ᴏɴ/ᴏꜰꜰ"
                )
        );
        inv.setItem(16, globalAntiItem);
        holder.setSlotAction(16, new AdminSlotAction("GLOBAL_INTERRUPT", null));

        // Slot 22: Back Button
        inv.setItem(22, createBackButton("Back to World Manager", "ʀᴇᴛᴜʀɴ ᴛᴏ ᴡᴏʀʟᴅ ʟɪsᴛ"));
        holder.setSlotAction(22, new AdminSlotAction("GLOBAL_BACK", null));
    }

    private ItemStack createGlobalTimerItem(
            Material mat,
            String spriteName,
            String title,
            boolean globalOn,
            int globalSec,
            int defaultSec,
            List<Integer> options
    ) {
        ItemStack item = createSimpleItem(
                mat,
                "<!italic><white><sprite:items:item/" + spriteName + "></white> <#89dceb><b>" + title + "</b>",
                List.of(
                        "<!italic><#6c7086>ɢʟᴏʙᴀʟ ᴏᴠᴇʀʀɪᴅᴇ",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>ɢʟᴏʙᴀʟ ꜰᴏʀᴄᴇ: " + formatBooleanStatus(globalOn),
                        "<!italic><#6c7086>▪ <#a6adc8>ꜰᴏʀᴄᴇ ᴅᴜʀᴀᴛɪᴏɴ: <#f9e2af><b>" + globalSec + "s</b>",
                        "<!italic><#6c7086>▪ <#a6adc8>ᴍɪɴ ᴅᴇꜰᴀᴜʟᴛ: <#89dceb><b>" + defaultSec + "s</b>",
                        "",
                        "<!italic><#6c7086>▪ <#a6adc8>ᴘʀᴇsᴇᴛs: " + buildOptionsBar(globalSec, options),
                        "",
                        "<!italic><white><glyph:left_click></white> <#a6e3a1>ɴᴇxᴛ <#6c7086>│ <white><glyph:right_click></white> <#f9e2af>ᴘʀᴇᴠɪᴏᴜs",
                        "<!italic><#89dceb>sʜɪꜰᴛ + <white><glyph:left_click></white> <#a6adc8>ᴛᴏɢɢʟᴇ ɢʟᴏʙᴀʟ ꜰᴏʀᴄᴇ",
                        "<!italic><#f38ba8>sʜɪꜰᴛ + <white><glyph:right_click></white> <#a6adc8>sᴇᴛ ᴅᴇꜰᴀᴜʟᴛ (" + globalSec + "s)"
                )
        );
        item.setAmount(Math.max(1, Math.min(64, globalSec)));
        return item;
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
