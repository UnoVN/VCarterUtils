package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.MaceManager;
import me.nh4n.vcarterutils.manager.PearlManager;
import me.nh4n.vcarterutils.manager.SettingMenuManager;
import me.nh4n.vcarterutils.manager.SettingMenuManager.AdminSettingMenuHolder;
import me.nh4n.vcarterutils.manager.SettingMenuManager.AdminSlotAction;
import me.nh4n.vcarterutils.manager.SettingMenuManager.MenuItemAction;
import me.nh4n.vcarterutils.manager.SettingMenuManager.SettingMenuHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.time.Duration;

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

            if (pearlManager.isWorldForced(player.getWorld())) {
                int forcedSec = pearlManager.getEffectiveCooldownSeconds(player);
                showLockedWarning(player, "pearl-cooldown", true, forcedSec);
                return;
            }
            if (pearlManager.isGlobalForceEnabled()) {
                int forcedSec = pearlManager.getEffectiveCooldownSeconds(player);
                showLockedWarning(player, "pearl-cooldown", false, forcedSec);
                return;
            }

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
                        "<glyph:warning_blue> <#a6adc8>Pearl cooldown set to <#89dceb><b>%seconds%s</b><#a6adc8>."
                );
                player.sendMessage(miniMessage.deserialize(raw.replace("%seconds%", String.valueOf(newSeconds))));
            }
            return;
        }

        if (type.equalsIgnoreCase("MACE_COOLDOWN")) {
            MaceManager maceManager = plugin.getMaceManager();

            if (maceManager.isWorldForced(player.getWorld())) {
                int forcedSec = maceManager.getEffectiveCooldownSeconds(player);
                showLockedWarning(player, "mace-cooldown", true, forcedSec);
                return;
            }
            if (maceManager.isGlobalForceEnabled()) {
                int forcedSec = maceManager.getEffectiveCooldownSeconds(player);
                showLockedWarning(player, "mace-cooldown", false, forcedSec);
                return;
            }

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
                        "<glyph:warning_blue> <#a6adc8>Mace cooldown set to <#89dceb><b>%seconds%s</b><#a6adc8>."
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

    public void showLockedWarning(Player player, String configPrefix, boolean isWorldForce, int forcedSec) {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);

        String titleRaw = plugin.getConfig().getString(
                configPrefix + ".messages.locked-title",
                "<#f38ba8><b>COOLDOWN LOCKED!</b>"
        );
        String subtitleRaw = isWorldForce
                ? plugin.getConfig().getString(
                        configPrefix + ".messages.locked-subtitle-world",
                        "<#f9e2af>Forced in this world: <#89dceb><b>%forced%s</b>"
                  )
                : plugin.getConfig().getString(
                        configPrefix + ".messages.locked-subtitle-global",
                        "<#f9e2af>Forced globally by Admin: <#89dceb><b>%forced%s</b>"
                  );

        Component titleComp = miniMessage.deserialize(titleRaw.replace("%forced%", String.valueOf(forcedSec)));
        Component subtitleComp = miniMessage.deserialize(
                subtitleRaw.replace("%forced%", String.valueOf(forcedSec))
                           .replace("%world%", player.getWorld().getName())
        );

        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(400));
        player.showTitle(Title.title(titleComp, subtitleComp, times));

        String chatRaw = isWorldForce
                ? plugin.getConfig().getString(
                        configPrefix + ".messages.locked-chat-world",
                        "<glyph:warning_blue> <#f38ba8><b>CANNOT CHANGE!</b> <#a6adc8>Cooldown is forced to <#89dceb><b>%forced%s</b> <#a6adc8>in this world."
                  )
                : plugin.getConfig().getString(
                        configPrefix + ".messages.locked-chat-global",
                        "<glyph:warning_blue> <#f38ba8><b>CANNOT CHANGE!</b> <#a6adc8>Cooldown is forced globally to <#89dceb><b>%forced%s</b><#a6adc8>."
                  );

        player.sendMessage(miniMessage.deserialize(
                chatRaw.replace("%forced%", String.valueOf(forcedSec))
                       .replace("%world%", player.getWorld().getName())
        ));
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
                    String statusStr = nextState
                            ? "<#a6e3a1><b>ON</b> <#6c7086>(" + pearlManager.getGlobalForceSeconds() + "s)"
                            : "<#f38ba8><b>OFF</b>";
                    String raw = plugin.getConfig().getString(
                            "admin-menu.messages.global-force-toggled",
                            "<glyph:warning_blue> <#a6adc8>Global %type% Force: %status%"
                    );
                    admin.sendMessage(miniMessage.deserialize(raw.replace("%type%", "Pearl").replace("%status%", statusStr)));
                } else if (click == ClickType.SHIFT_RIGHT) {
                    int sec = pearlManager.getGlobalForceSeconds();
                    pearlManager.setDefaultSeconds(sec);
                    admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
                    String raw = plugin.getConfig().getString(
                            "admin-menu.messages.default-updated",
                            "<glyph:warning_blue> <#a6e3a1>Default %type% cooldown set to <#89dceb><b>%seconds%s</b><#a6e3a1>."
                    );
                    admin.sendMessage(miniMessage.deserialize(raw.replace("%type%", "Pearl").replace("%seconds%", String.valueOf(sec))));
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
                    String statusStr = nextState
                            ? "<#a6e3a1><b>ON</b> <#6c7086>(" + maceManager.getGlobalForceSeconds() + "s)"
                            : "<#f38ba8><b>OFF</b>";
                    String raw = plugin.getConfig().getString(
                            "admin-menu.messages.global-force-toggled",
                            "<glyph:warning_blue> <#a6adc8>Global %type% Force: %status%"
                    );
                    admin.sendMessage(miniMessage.deserialize(raw.replace("%type%", "Mace").replace("%status%", statusStr)));
                } else if (click == ClickType.SHIFT_RIGHT) {
                    int sec = maceManager.getGlobalForceSeconds();
                    maceManager.setDefaultSeconds(sec);
                    admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
                    String raw = plugin.getConfig().getString(
                            "admin-menu.messages.default-updated",
                            "<glyph:warning_blue> <#a6e3a1>Default %type% cooldown set to <#89dceb><b>%seconds%s</b><#a6e3a1>."
                    );
                    admin.sendMessage(miniMessage.deserialize(raw.replace("%type%", "Mace").replace("%seconds%", String.valueOf(sec))));
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

                String raw = plugin.getConfig().getString(
                        "admin-menu.messages.world-force-updated",
                        "<glyph:warning_blue> <#89dceb><b>%world%</b> <#a6adc8>%type% Force: %status%"
                );

                if (click == ClickType.SHIFT_LEFT) {
                    pearlManager.setWorldForceSeconds(worldName, -1);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 1.2f);
                    admin.sendMessage(miniMessage.deserialize(
                            raw.replace("%world%", worldName).replace("%type%", "Pearl").replace("%status%", "<#f38ba8><b>OFF</b>")
                    ));
                } else if (click == ClickType.SHIFT_RIGHT) {
                    maceManager.setWorldForceSeconds(worldName, -1);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 1.2f);
                    admin.sendMessage(miniMessage.deserialize(
                            raw.replace("%world%", worldName).replace("%type%", "Mace").replace("%status%", "<#f38ba8><b>OFF</b>")
                    ));
                } else if (click.isRightClick()) {
                    int newMaceSec = maceManager.cycleWorldForceSeconds(worldName);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
                    String valStr = (newMaceSec < 0) ? "<#f38ba8><b>OFF</b>" : "<#a6e3a1><b>" + newMaceSec + "s</b>";
                    admin.sendMessage(miniMessage.deserialize(
                            raw.replace("%world%", worldName).replace("%type%", "Mace").replace("%status%", valStr)
                    ));
                } else {
                    int newPearlSec = pearlManager.cycleWorldForceSeconds(worldName);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                    String valStr = (newPearlSec < 0) ? "<#f38ba8><b>OFF</b>" : "<#a6e3a1><b>" + newPearlSec + "s</b>";
                    admin.sendMessage(miniMessage.deserialize(
                            raw.replace("%world%", worldName).replace("%type%", "Pearl").replace("%status%", valStr)
                    ));
                }
                menuManager.refreshAdminMenu(admin);
            }

            case "RESCAN" -> {
                menuManager.refreshAdminMenu(admin);
                admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
                String raw = plugin.getConfig().getString(
                        "admin-menu.messages.world-rescanned",
                        "<glyph:warning_blue> <#a6e3a1>World list rescanned."
                );
                admin.sendMessage(miniMessage.deserialize(raw));
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
