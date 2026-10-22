package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SettingMenuManager {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    public SettingMenuManager(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void reload() {
        // Cap nhat lai cac giao dien /setting dang mo neu admin go /vcu reload
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof SettingMenuHolder holder) {
                populateInventory(online, holder.getInventory(), holder);
            }
        }
    }

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

        // 1. Lap day cac o trong bang Filler Item neu duoc bat trong config
        if (plugin.getConfig().getBoolean("setting-menu.filler.enabled", true)) {
            String fillerMatName = plugin.getConfig().getString("setting-menu.filler.material", "GRAY_STAINED_GLASS_PANE");
            Material fillerMat = Material.matchMaterial(fillerMatName != null ? fillerMatName : "GRAY_STAINED_GLASS_PANE");
            if (fillerMat == null) {
                fillerMat = Material.GRAY_STAINED_GLASS_PANE;
            }

            ItemStack filler = new ItemStack(fillerMat);
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

        // 2. Doc danh sach cac vat pham trong setting-menu.items tu config.yml
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
            String matName = itemSec.getString("material", "ENDER_PEARL");
            Material mat = Material.matchMaterial(matName != null ? matName : "ENDER_PEARL");
            if (mat == null || mat == Material.AIR) {
                mat = Material.STONE;
            }

            ItemStack stack = new ItemStack(mat);
            ItemMeta meta = stack.getItemMeta();

            if (meta != null) {
                String nameRaw = itemSec.getString("name", "<#89dceb><b>Setting Item</b>");
                meta.displayName(miniMessage.deserialize(applyPlaceholders(player, nameRaw)));

                List<String> loreRaw = itemSec.getStringList("lore");
                List<Component> loreComp = new ArrayList<>();
                for (String line : loreRaw) {
                    loreComp.add(miniMessage.deserialize(applyPlaceholders(player, line)));
                }
                meta.lore(loreComp);

                int customModelData = itemSec.getInt("custom-model-data", 0);
                if (customModelData > 0) {
                    meta.setCustomModelData(customModelData);
                }

                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                stack.setItemMeta(meta);
            }

            // Neu la item PEARL_COOLDOWN va bat hien thi so giay bang so luong item
            if (type.equals("PEARL_COOLDOWN") && itemSec.getBoolean("show-seconds-as-amount", true)) {
                int currentSec = plugin.getPearlManager().getPlayerPersonalSeconds(player.getUniqueId());
                stack.setAmount(Math.max(1, Math.min(64, currentSec)));
            }

            inv.setItem(slot, stack);
            holder.setSlotAction(slot, new MenuItemAction(key, type, itemSec.getString("command", "")));
        }
    }

    private String applyPlaceholders(Player player, String input) {
        if (input == null) {
            return "";
        }

        PearlManager pearlManager = plugin.getPearlManager();
        int personalSec = pearlManager.getPlayerPersonalSeconds(player.getUniqueId());
        int defaultSec = pearlManager.getDefaultSeconds();
        int effectiveSec = pearlManager.getEffectiveCooldownSeconds(player);
        boolean isWorldForced = pearlManager.isWorldForced(player.getWorld());

        String worldStatus;
        if (isWorldForced) {
            String forcedFormat = plugin.getConfig().getString(
                    "pearl-cooldown.messages.world-status-forced",
                    "<#f9e2af>⚠ Forced in this world: <#89dceb><b>%forced%s</b>"
            );
            worldStatus = forcedFormat.replace("%forced%", String.valueOf(effectiveSec))
                                      .replace("%world%", player.getWorld().getName());
        } else {
            worldStatus = plugin.getConfig().getString(
                    "pearl-cooldown.messages.world-status-normal",
                    "<#a6e3a1>✔ Active in current world"
            );
        }

        String optionsBar = buildOptionsBar(personalSec, pearlManager.getAvailableOptions());

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

    public record MenuItemAction(String key, String type, String command) {
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
}
