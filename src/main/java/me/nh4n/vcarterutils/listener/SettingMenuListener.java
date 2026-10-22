package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.MaceManager;
import me.nh4n.vcarterutils.manager.PearlManager;
import me.nh4n.vcarterutils.manager.SettingMenuManager;
import me.nh4n.vcarterutils.manager.SettingMenuManager.AdminSettingMenuHolder;
import me.nh4n.vcarterutils.manager.SettingMenuManager.AdminSlotAction;
import me.nh4n.vcarterutils.manager.SettingMenuManager.MenuItemAction;
import me.nh4n.vcarterutils.manager.SettingMenuManager.SettingMenuHolder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class SettingMenuListener implements Listener {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    public SettingMenuListener(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder topHolder = event.getView().getTopInventory().getHolder();

        // 1. Xu ly Player /setting Menu
        if (topHolder instanceof SettingMenuHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
                return;
            }
            handlePlayerMenuClick(player, holder, event.getSlot(), event.getClick());
            return;
        }

        // 2. Xu ly Admin /setting admin Menu
        if (topHolder instanceof AdminSettingMenuHolder adminHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player admin)) {
                return;
            }
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
                return;
            }
            handleAdminMenuClick(admin, adminHolder, event.getSlot(), event.getClick());
        }
    }

    private void handlePlayerMenuClick(Player player, SettingMenuHolder holder, int slot, ClickType click) {
        MenuItemAction action = holder.getSlotAction(slot);
        if (action == null) {
            return;
        }

        String type = action.type();
        SettingMenuManager menuManager = plugin.getSettingMenuManager();

        if (type.equalsIgnoreCase("PEARL_COOLDOWN")) {
            PearlManager pearlManager = plugin.getPearlManager();
            int newSeconds;

            if (click.isShiftClick()) {
                newSeconds = pearlManager.resetToDefault(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            } else if (click.isRightClick()) {
                newSeconds = pearlManager.cyclePreviousOption(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
            } else {
                newSeconds = pearlManager.cycleNextOption(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }

            menuManager.refreshMenu(player);

            if (plugin.getConfig().getBoolean("pearl-cooldown.send-chat-on-change", true)) {
                String raw = plugin.getConfig().getString(
                        "pearl-cooldown.messages.cooldown-changed",
                        "<glyph:warning_blue> <#a6e3a1>Your personal Ender Pearl cooldown has been set to <#89dceb><b>%seconds%s</b><#a6e3a1>!"
                );
                player.sendMessage(miniMessage.deserialize(raw.replace("%seconds%", String.valueOf(newSeconds))));
            }
            return;
        }

        if (type.equalsIgnoreCase("MACE_COOLDOWN")) {
            MaceManager maceManager = plugin.getMaceManager();
            int newSeconds;

            if (click.isShiftClick()) {
                newSeconds = maceManager.resetToDefault(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            } else if (click.isRightClick()) {
                newSeconds = maceManager.cyclePreviousOption(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
            } else {
                newSeconds = maceManager.cycleNextOption(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }

            menuManager.refreshMenu(player);

            if (plugin.getConfig().getBoolean("mace-cooldown.send-chat-on-change", true)) {
                String raw = plugin.getConfig().getString(
                        "mace-cooldown.messages.cooldown-changed",
                        "<glyph:warning_blue> <#a6e3a1>Your personal Mace cooldown has been set to <#89dceb><b>%seconds%s</b><#a6e3a1>!"
                );
                player.sendMessage(miniMessage.deserialize(raw.replace("%seconds%", String.valueOf(newSeconds))));
            }
            return;
        }

        if (type.equalsIgnoreCase("ADMIN_MENU")) {
            if (player.hasPermission("vcarterutils.admin")) {
                menuManager.openAdminSettingMenu(player, 0);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            return;
        }

        if (type.equalsIgnoreCase("CLOSE")) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        if (type.equalsIgnoreCase("COMMAND")) {
            String cmd = action.command();
            if (cmd != null && !cmd.trim().isEmpty()) {
                player.closeInventory();
                String cleanCmd = cmd.trim().startsWith("/") ? cmd.trim().substring(1) : cmd.trim();
                player.performCommand(cleanCmd.replace("%player%", player.getName()));
            }
        }
    }

    private void handleAdminMenuClick(Player admin, AdminSettingMenuHolder holder, int slot, ClickType click) {
        if (!admin.hasPermission("vcarterutils.admin")) {
            admin.closeInventory();
            return;
        }

        AdminSlotAction action = holder.getSlotAction(slot);
        if (action == null) {
            return;
        }

        String actType = action.actionType();
        PearlManager pearlManager = plugin.getPearlManager();
        MaceManager maceManager = plugin.getMaceManager();
        SettingMenuManager menuManager = plugin.getSettingMenuManager();

        switch (actType) {
            case "GLOBAL_PEARL" -> {
                if (click == ClickType.SHIFT_LEFT) {
                    boolean nextState = !pearlManager.isGlobalForceEnabled();
                    pearlManager.setGlobalForceEnabled(nextState);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, nextState ? 1.5f : 0.8f);
                    admin.sendMessage(miniMessage.deserialize(
                            "<glyph:warning_blue> <#a6adc8>Global Pearl Force Cooldown is now " +
                            (nextState ? "<#a6e3a1><b>ENABLED</b> <#6c7086>(" + pearlManager.getGlobalForceSeconds() + "s)" : "<#f38ba8><b>DISABLED</b>")
                    ));
                } else if (click == ClickType.SHIFT_RIGHT) {
                    int sec = pearlManager.getGlobalForceSeconds();
                    pearlManager.setDefaultSeconds(sec);
                    admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
                    admin.sendMessage(miniMessage.deserialize(
                            "<glyph:warning_blue> <#a6e3a1>Default Pearl Cooldown updated to <#89dceb><b>" + sec + "s</b><#a6e3a1>!"
                    ));
                } else if (click.isRightClick()) {
                    pearlManager.cycleGlobalForceSeconds(false);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                } else {
                    pearlManager.cycleGlobalForceSeconds(true);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                }
                menuManager.refreshAdminMenu(admin);
            }

            case "GLOBAL_MACE" -> {
                if (click == ClickType.SHIFT_LEFT) {
                    boolean nextState = !maceManager.isGlobalForceEnabled();
                    maceManager.setGlobalForceEnabled(nextState);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, nextState ? 1.5f : 0.8f);
                    admin.sendMessage(miniMessage.deserialize(
                            "<glyph:warning_blue> <#a6adc8>Global Mace Force Cooldown is now " +
                            (nextState ? "<#a6e3a1><b>ENABLED</b> <#6c7086>(" + maceManager.getGlobalForceSeconds() + "s)" : "<#f38ba8><b>DISABLED</b>")
                    ));
                } else if (click == ClickType.SHIFT_RIGHT) {
                    int sec = maceManager.getGlobalForceSeconds();
                    maceManager.setDefaultSeconds(sec);
                    admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
                    admin.sendMessage(miniMessage.deserialize(
                            "<glyph:warning_blue> <#a6e3a1>Default Mace Cooldown updated to <#89dceb><b>" + sec + "s</b><#a6e3a1>!"
                    ));
                } else if (click.isRightClick()) {
                    maceManager.cycleGlobalForceSeconds(false);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                } else {
                    maceManager.cycleGlobalForceSeconds(true);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                }
                menuManager.refreshAdminMenu(admin);
            }

            case "WORLD_ITEM" -> {
                String worldName = action.targetWorld();
                if (worldName == null) return;

                if (click == ClickType.SHIFT_LEFT) {
                    pearlManager.setWorldForceSeconds(worldName, -1);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 1.2f);
                    admin.sendMessage(miniMessage.deserialize(
                            "<glyph:warning_blue> <#f38ba8>Disabled Forced Pearl Cooldown for world <#89dceb><b>" + worldName + "</b><#f38ba8>."
                    ));
                } else if (click == ClickType.SHIFT_RIGHT) {
                    maceManager.setWorldForceSeconds(worldName, -1);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 1.2f);
                    admin.sendMessage(miniMessage.deserialize(
                            "<glyph:warning_blue> <#f38ba8>Disabled Forced Mace Cooldown for world <#89dceb><b>" + worldName + "</b><#f38ba8>."
                    ));
                } else if (click.isRightClick()) {
                    int newMaceSec = maceManager.cycleWorldForceSeconds(worldName);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
                    if (newMaceSec < 0) {
                        admin.sendMessage(miniMessage.deserialize(
                                "<glyph:warning_blue> <#f38ba8>Disabled Forced Mace Cooldown for world <#89dceb><b>" + worldName + "</b><#f38ba8>."
                        ));
                    } else {
                        admin.sendMessage(miniMessage.deserialize(
                                "<glyph:warning_blue> <#a6e3a1>Set Forced Mace Cooldown for world <#89dceb><b>" + worldName + "</b> <#a6e3a1>to <#f9e2af><b>" + newMaceSec + "s</b><#a6e3a1>!"
                        ));
                    }
                } else {
                    int newPearlSec = pearlManager.cycleWorldForceSeconds(worldName);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                    if (newPearlSec < 0) {
                        admin.sendMessage(miniMessage.deserialize(
                                "<glyph:warning_blue> <#f38ba8>Disabled Forced Pearl Cooldown for world <#89dceb><b>" + worldName + "</b><#f38ba8>."
                        ));
                    } else {
                        admin.sendMessage(miniMessage.deserialize(
                                "<glyph:warning_blue> <#a6e3a1>Set Forced Pearl Cooldown for world <#89dceb><b>" + worldName + "</b> <#a6e3a1>to <#f9e2af><b>" + newPearlSec + "s</b><#a6e3a1>!"
                        ));
                    }
                }
                menuManager.refreshAdminMenu(admin);
            }

            case "RESCAN" -> {
                menuManager.refreshAdminMenu(admin);
                admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
                admin.sendMessage(miniMessage.deserialize("<glyph:warning_blue> <#a6e3a1>World list rescanned and updated!"));
            }

            case "PREV_PAGE" -> {
                holder.setPage(Math.max(0, holder.getPage() - 1));
                menuManager.refreshAdminMenu(admin);
                admin.playSound(admin.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            }

            case "NEXT_PAGE" -> {
                holder.setPage(holder.getPage() + 1);
                menuManager.refreshAdminMenu(admin);
                admin.playSound(admin.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            }

            case "OPEN_PLAYER_MENU" -> {
                menuManager.openSettingMenu(admin);
                admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
            }

            case "CLOSE" -> {
                admin.closeInventory();
                admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder topHolder = event.getView().getTopInventory().getHolder();
        if (topHolder instanceof SettingMenuHolder || topHolder instanceof AdminSettingMenuHolder) {
            event.setCancelled(true);
        }
    }
}
