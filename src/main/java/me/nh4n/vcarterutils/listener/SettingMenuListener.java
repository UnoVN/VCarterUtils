package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.CombatTagManager;
import me.nh4n.vcarterutils.manager.MaceManager;
import me.nh4n.vcarterutils.manager.PearlManager;
import me.nh4n.vcarterutils.manager.SettingMenuManager;
import me.nh4n.vcarterutils.manager.SettingMenuManager.AdminSettingMenuHolder;
import me.nh4n.vcarterutils.manager.SettingMenuManager.AdminSlotAction;
import me.nh4n.vcarterutils.manager.SettingMenuManager.GlobalDetailMenuHolder;
import me.nh4n.vcarterutils.manager.SettingMenuManager.MenuItemAction;
import me.nh4n.vcarterutils.manager.SettingMenuManager.SettingMenuHolder;
import me.nh4n.vcarterutils.manager.SettingMenuManager.WorldDetailMenuHolder;
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

import me.nh4n.vcarterutils.hook.FoCombatTagHook;
import me.nh4n.vcarterutils.manager.TrainingManager.TrainingMenuHolder;
import me.nh4n.vcarterutils.manager.TrainingOption;

import java.time.Duration;
import java.util.List;

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
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
            handlePlayerMenuClick(player, holder, event.getSlot(), event.getClick());
            return;
        }

        if (topHolder instanceof AdminSettingMenuHolder adminHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player admin)) return;
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
            handleAdminMenuClick(admin, adminHolder, event.getSlot(), event.getClick(), event.getHotbarButton());
            return;
        }

        if (topHolder instanceof WorldDetailMenuHolder detailHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player admin)) return;
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
            handleWorldDetailMenuClick(admin, detailHolder, event.getSlot(), event.getClick());
            return;
        }

        if (topHolder instanceof GlobalDetailMenuHolder globalHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player admin)) return;
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
            handleGlobalDetailMenuClick(admin, globalHolder, event.getSlot(), event.getClick());
            return;
        }

        if (topHolder instanceof TrainingMenuHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;
            handleTrainingMenuClick(player, event.getSlot(), event.getClick());
            return;
        }
    }

    private void handlePlayerMenuClick(Player player, SettingMenuHolder holder, int slot, ClickType click) {
        MenuItemAction action = holder.getSlotAction(slot);
        if (action == null) return;

        String type = action.type();
        SettingMenuManager menuManager = plugin.getSettingMenuManager();

        if (type.equalsIgnoreCase("PEARL_COOLDOWN")) {
            PearlManager pearlManager = plugin.getPearlManager();

            if (pearlManager.isWorldForced(player.getWorld())) {
                showLockedWarning(player, "pearl-cooldown", true, pearlManager.getEffectiveCooldownSeconds(player));
                return;
            }
            if (pearlManager.isGlobalForceEnabled()) {
                showLockedWarning(player, "pearl-cooldown", false, pearlManager.getEffectiveCooldownSeconds(player));
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
                        "<white><sprite:items:item/ender_pearl></white> %c_text%Pearl cooldown set to %c_primary%<b>%seconds%s</b>%c_text%."
                );
                player.sendMessage(plugin.parseMessage(raw.replace("%seconds%", String.valueOf(newSeconds))));
            }
            return;
        }

        if (type.equalsIgnoreCase("MACE_COOLDOWN")) {
            MaceManager maceManager = plugin.getMaceManager();

            if (maceManager.isWorldForced(player.getWorld())) {
                showLockedWarning(player, "mace-cooldown", true, maceManager.getEffectiveCooldownSeconds(player));
                return;
            }
            if (maceManager.isGlobalForceEnabled()) {
                showLockedWarning(player, "mace-cooldown", false, maceManager.getEffectiveCooldownSeconds(player));
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
                        "<white><sprite:items:item/mace></white> %c_text%Mace cooldown set to %c_primary%<b>%seconds%s</b>%c_text%."
                );
                player.sendMessage(plugin.parseMessage(raw.replace("%seconds%", String.valueOf(newSeconds))));
            }
            return;
        }

        if (type.equalsIgnoreCase("LOBBY_HOTBAR_TOGGLE")) {
            boolean enabled = plugin.getLobbyHotbarManager().togglePlayerSetting(player);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, enabled ? 1.5f : 0.8f);
            menuManager.refreshMenu(player);

            String statusStr = enabled
                    ? plugin.getConfig().getString("lobby-hotbar.messages.status-enabled", "%c_success%<b>✔ ᴇɴᴀʙʟᴇᴅ</b>")
                    : plugin.getConfig().getString("lobby-hotbar.messages.status-disabled", "%c_error%<b>✖ ᴅɪsᴀʙʟᴇᴅ</b>");
            String raw = plugin.getConfig().getString(
                    "lobby-hotbar.messages.toggled",
                    "%prefix_info%%c_text%Lobby Hotbar %c_muted%» %status%"
            );
            player.sendMessage(plugin.parseMessage(raw.replace("%status%", statusStr)));
            return;
        }

        if (type.equalsIgnoreCase("XBOW_COUNTER_TOGGLE")) {
            boolean enabled = plugin.getXbowManager().togglePlayerSetting(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, enabled ? 1.5f : 0.8f);
            menuManager.refreshMenu(player);

            String statusStr = enabled
                    ? plugin.getConfig().getString("xbow-counter.messages.status-enabled", "%c_success%<b>✔ ᴇɴᴀʙʟᴇᴅ</b>")
                    : plugin.getConfig().getString("xbow-counter.messages.status-disabled", "%c_error%<b>✖ ᴅɪsᴀʙʟᴇᴅ</b>");
            String raw = plugin.getConfig().getString(
                    "xbow-counter.messages.toggled",
                    "%prefix_info%%c_text%Xbow Tick Counter %c_muted%» %status%"
            );
            player.sendMessage(plugin.parseMessage(raw.replace("%status%", statusStr)));
            return;
        }

        if (type.equalsIgnoreCase("TRAINING_MODE_TOGGLE")) {
            if (plugin.getConfig().getBoolean("training.disable-in-combat", true) && FoCombatTagHook.isTagged(player)) {
                String combatMsg = plugin.getConfig().getString(
                        "training.messages.cannot-in-combat",
                        "%prefix_combat%%c_error%<b>ɪɴ ᴄᴏᴍʙᴀᴛ</b> %c_muted%» %c_text%You cannot use Training Mode during combat."
                );
                player.sendMessage(plugin.parseMessage(combatMsg));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            if (plugin.getTrainingManager().isWorldBlacklisted(player.getWorld().getName())) {
                String blMsg = plugin.getConfig().getString(
                        "training.messages.blacklisted-world",
                        "%prefix_warning%%c_error%<b>ʀᴇsᴛʀɪᴄᴛᴇᴅ</b> %c_muted%» %c_text%Training Mode is disabled in this world."
                );
                player.sendMessage(plugin.parseMessage(blMsg));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            if (click.isRightClick() || click.isShiftClick()) {
                boolean enabled = plugin.getTrainingManager().toggleMaster(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, enabled ? 1.5f : 0.8f);
                menuManager.refreshMenu(player);

                String statusStr = enabled
                        ? plugin.getConfig().getString("training.messages.status-enabled", "%c_success%<b>✔ ᴇɴᴀʙʟᴇᴅ</b>")
                        : plugin.getConfig().getString("training.messages.status-disabled", "%c_error%<b>✖ ᴅɪsᴀʙʟᴇᴅ</b>");
                String raw = plugin.getConfig().getString(
                        "training.messages.toggled",
                        "%prefix_info%%c_text%Training Mode %c_muted%» %status%"
                );
                player.sendMessage(plugin.parseMessage(raw.replace("%status%", statusStr)));
            } else {
                plugin.getTrainingManager().openTrainingMenu(player);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
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
                "%c_error%<b>ᴄᴏᴏʟᴅᴏᴡɴ ʟᴏᴄᴋᴇᴅ</b>"
        );
        String subtitleRaw = isWorldForce
                ? plugin.getConfig().getString(
                        configPrefix + ".messages.locked-subtitle-world",
                        "%c_text%Forced in %c_primary%%world% %c_muted%» %c_warning%<b>%forced%s</b>"
                  )
                : plugin.getConfig().getString(
                        configPrefix + ".messages.locked-subtitle-global",
                        "%c_text%Forced Globally %c_muted%» %c_warning%<b>%forced%s</b>"
                  );

        Component titleComp = plugin.parseMessage(titleRaw.replace("%forced%", String.valueOf(forcedSec)));
        Component subtitleComp = plugin.parseMessage(
                subtitleRaw.replace("%forced%", String.valueOf(forcedSec))
                           .replace("%world%", player.getWorld().getName())
        );

        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(400));
        player.showTitle(Title.title(titleComp, subtitleComp, times));

        String chatRaw = isWorldForce
                ? plugin.getConfig().getString(
                        configPrefix + ".messages.locked-chat-world",
                        "%prefix_error%%c_error%<b>ʟᴏᴄᴋᴇᴅ</b> %c_muted%» %c_text%Cooldown is forced to %c_primary%<b>%forced%s</b> %c_text%in this world."
                  )
                : plugin.getConfig().getString(
                        configPrefix + ".messages.locked-chat-global",
                        "%prefix_error%%c_error%<b>ʟᴏᴄᴋᴇᴅ</b> %c_muted%» %c_text%Cooldown is forced globally to %c_primary%<b>%forced%s</b>%c_text%."
                  );

        player.sendMessage(plugin.parseMessage(
                chatRaw.replace("%forced%", String.valueOf(forcedSec))
                       .replace("%world%", player.getWorld().getName())
        ));
    }

    private void handleAdminMenuClick(Player admin, AdminSettingMenuHolder holder, int slot, ClickType click, int hotbarButton) {
        if (!admin.hasPermission("vcarterutils.admin")) {
            admin.closeInventory();
            return;
        }

        AdminSlotAction action = holder.getSlotAction(slot);
        if (action == null) return;

        String actType = action.actionType();
        SettingMenuManager menuManager = plugin.getSettingMenuManager();

        switch (actType) {
            case "OPEN_WORLD_DETAIL", "WORLD_ITEM" -> {
                String worldName = action.targetWorld();
                if (worldName == null) return;
                menuManager.openWorldDetailMenu(admin, worldName);
                admin.playSound(admin.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
            }

            case "OPEN_GLOBAL_DETAIL" -> {
                menuManager.openGlobalDetailMenu(admin);
                admin.playSound(admin.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
            }

            case "RESCAN" -> {
                menuManager.refreshAdminMenu(admin);
                admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
                String raw = plugin.getConfig().getString(
                        "admin-menu.messages.world-rescanned",
                        "%prefix_admin%%c_success%World list rescanned and synchronized."
                );
                admin.sendMessage(plugin.parseMessage(raw));
            }

            case "OPEN_PLAYER_MENU" -> {
                menuManager.openSettingMenu(admin);
                admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
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

            case "CLOSE" -> {
                admin.closeInventory();
                admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
        }
    }

    private void handleWorldDetailMenuClick(Player admin, WorldDetailMenuHolder holder, int slot, ClickType click) {
        if (!admin.hasPermission("vcarterutils.admin")) {
            admin.closeInventory();
            return;
        }

        AdminSlotAction action = holder.getSlotAction(slot);
        if (action == null) return;

        String actType = action.actionType();
        String worldName = holder.getWorldName();
        PearlManager pearlManager = plugin.getPearlManager();
        MaceManager maceManager = plugin.getMaceManager();
        CombatTagManager combatManager = plugin.getCombatTagManager();
        SettingMenuManager menuManager = plugin.getSettingMenuManager();

        String raw = plugin.getConfig().getString(
                "admin-menu.messages.world-force-updated",
                "%prefix_admin%%c_primary%<b>%world%</b> %c_muted%» %c_text%%type% Force: %status%"
        );

        switch (actType) {
            case "DETAIL_PEARL" -> {
                if (click.isShiftClick()) {
                    pearlManager.setWorldForceSeconds(worldName, -1);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 1.2f);
                    admin.sendMessage(plugin.parseMessage(raw.replace("%world%", worldName).replace("%type%", "Pearl").replace("%status%", "<#f38ba8><b>✖ ᴏꜰꜰ</b>")));
                } else if (click.isRightClick()) {
                    int newSec = pearlManager.cycleWorldForceSeconds(worldName, false);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                    String valStr = (newSec < 0) ? "<#f38ba8><b>✖ ᴏꜰꜰ</b>" : "<#a6e3a1><b>✔ " + newSec + "s</b>";
                    admin.sendMessage(plugin.parseMessage(raw.replace("%world%", worldName).replace("%type%", "Pearl").replace("%status%", valStr)));
                } else {
                    int newSec = pearlManager.cycleWorldForceSeconds(worldName, true);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                    String valStr = (newSec < 0) ? "<#f38ba8><b>✖ ᴏꜰꜰ</b>" : "<#a6e3a1><b>✔ " + newSec + "s</b>";
                    admin.sendMessage(plugin.parseMessage(raw.replace("%world%", worldName).replace("%type%", "Pearl").replace("%status%", valStr)));
                }
                menuManager.refreshWorldDetailMenu(admin);
            }

            case "DETAIL_MACE" -> {
                if (click.isShiftClick()) {
                    maceManager.setWorldForceSeconds(worldName, -1);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 1.2f);
                    admin.sendMessage(plugin.parseMessage(raw.replace("%world%", worldName).replace("%type%", "Mace").replace("%status%", "<#f38ba8><b>✖ ᴏꜰꜰ</b>")));
                } else if (click.isRightClick()) {
                    int newSec = maceManager.cycleWorldForceSeconds(worldName, false);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                    String valStr = (newSec < 0) ? "<#f38ba8><b>✖ ᴏꜰꜰ</b>" : "<#a6e3a1><b>✔ " + newSec + "s</b>";
                    admin.sendMessage(plugin.parseMessage(raw.replace("%world%", worldName).replace("%type%", "Mace").replace("%status%", valStr)));
                } else {
                    int newSec = maceManager.cycleWorldForceSeconds(worldName, true);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                    String valStr = (newSec < 0) ? "<#f38ba8><b>✖ ᴏꜰꜰ</b>" : "<#a6e3a1><b>✔ " + newSec + "s</b>";
                    admin.sendMessage(plugin.parseMessage(raw.replace("%world%", worldName).replace("%type%", "Mace").replace("%status%", valStr)));
                }
                menuManager.refreshWorldDetailMenu(admin);
            }

            case "DETAIL_COMBAT" -> {
                if (click == ClickType.SHIFT_LEFT) {
                    combatManager.setWorldForceSeconds(worldName, 0);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                    admin.sendMessage(plugin.parseMessage(raw.replace("%world%", worldName).replace("%type%", "Combat Tag").replace("%status%", "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ (0s)</b>")));
                } else if (click == ClickType.SHIFT_RIGHT) {
                    combatManager.setWorldForceSeconds(worldName, -1);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 1.2f);
                    admin.sendMessage(plugin.parseMessage(raw.replace("%world%", worldName).replace("%type%", "Combat Tag").replace("%status%", "<#6c7086>ᴅᴇꜰᴀᴜʟᴛ")));
                } else if (click.isRightClick()) {
                    int newSec = combatManager.cycleWorldForceSeconds(worldName, false);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                    String valStr;
                    if (newSec == 0) valStr = "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ (0s)</b>";
                    else if (newSec > 0) valStr = "<#a6e3a1><b>✔ " + newSec + "s</b>";
                    else valStr = "<#6c7086>ᴅᴇꜰᴀᴜʟᴛ";
                    admin.sendMessage(plugin.parseMessage(raw.replace("%world%", worldName).replace("%type%", "Combat Tag").replace("%status%", valStr)));
                } else {
                    int newSec = combatManager.cycleWorldForceSeconds(worldName, true);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                    String valStr;
                    if (newSec == 0) valStr = "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ (0s)</b>";
                    else if (newSec > 0) valStr = "<#a6e3a1><b>✔ " + newSec + "s</b>";
                    else valStr = "<#6c7086>ᴅᴇꜰᴀᴜʟᴛ";
                    admin.sendMessage(plugin.parseMessage(raw.replace("%world%", worldName).replace("%type%", "Combat Tag").replace("%status%", valStr)));
                }
                menuManager.refreshWorldDetailMenu(admin);
            }

            case "DETAIL_INTERRUPT" -> {
                if (click.isShiftClick()) {
                    combatManager.setWorldAntiInterrupt(worldName, null);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 1.2f);
                    admin.sendMessage(plugin.parseMessage(raw.replace("%world%", worldName).replace("%type%", "Anti-Interrupt").replace("%status%", "<#6c7086>ᴅᴇꜰᴀᴜʟᴛ")));
                } else {
                    Boolean current = combatManager.getWorldAntiInterrupt(worldName);
                    boolean next;
                    if (current == null) {
                        next = !combatManager.isAntiInterruptGlobal();
                    } else {
                        next = !current;
                    }
                    combatManager.setWorldAntiInterrupt(worldName, next);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, next ? 1.5f : 0.8f);
                    String valStr = next ? "<#a6e3a1><b>✔ ᴇɴᴀʙʟᴇᴅ</b>" : "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ</b>";
                    admin.sendMessage(plugin.parseMessage(raw.replace("%world%", worldName).replace("%type%", "Anti-Interrupt").replace("%status%", valStr)));
                }
                menuManager.refreshWorldDetailMenu(admin);
            }

            case "DETAIL_BACK" -> {
                menuManager.openAdminSettingMenu(admin, 0);
                admin.playSound(admin.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            }
        }
    }

    private void handleGlobalDetailMenuClick(Player admin, GlobalDetailMenuHolder holder, int slot, ClickType click) {
        if (!admin.hasPermission("vcarterutils.admin")) {
            admin.closeInventory();
            return;
        }

        AdminSlotAction action = holder.getSlotAction(slot);
        if (action == null) return;

        String actType = action.actionType();
        PearlManager pearlManager = plugin.getPearlManager();
        MaceManager maceManager = plugin.getMaceManager();
        CombatTagManager combatManager = plugin.getCombatTagManager();
        SettingMenuManager menuManager = plugin.getSettingMenuManager();

        switch (actType) {
            case "GLOBAL_PEARL" -> {
                if (click == ClickType.SHIFT_LEFT) {
                    boolean nextState = !pearlManager.isGlobalForceEnabled();
                    pearlManager.setGlobalForceEnabled(nextState);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, nextState ? 1.5f : 0.8f);
                    String statusStr = nextState
                            ? "<#a6e3a1><b>✔ ᴏɴ</b> <#6c7086>(" + pearlManager.getGlobalForceSeconds() + "s)"
                            : "<#f38ba8><b>✖ ᴏꜰꜰ</b>";
                    String raw = plugin.getConfig().getString(
                            "admin-menu.messages.global-force-toggled",
                            "%prefix_admin%%c_text%Global %c_primary%<b>%type%</b> %c_text%Force %c_muted%» %status%"
                    );
                    admin.sendMessage(plugin.parseMessage(raw.replace("%type%", "Pearl").replace("%status%", statusStr)));
                } else if (click == ClickType.SHIFT_RIGHT) {
                    int sec = pearlManager.getGlobalForceSeconds();
                    pearlManager.setDefaultSeconds(sec);
                    admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
                    String raw = plugin.getConfig().getString(
                            "admin-menu.messages.default-updated",
                            "%prefix_admin%%c_text%Default %c_primary%<b>%type%</b> %c_text%cooldown set to %c_primary%<b>%seconds%s</b>%c_text%."
                    );
                    admin.sendMessage(plugin.parseMessage(raw.replace("%type%", "Pearl").replace("%seconds%", String.valueOf(sec))));
                } else if (click.isRightClick()) {
                    pearlManager.cycleGlobalForceSeconds(false);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                } else {
                    pearlManager.cycleGlobalForceSeconds(true);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                }
                menuManager.refreshGlobalDetailMenu(admin);
            }

            case "GLOBAL_MACE" -> {
                if (click == ClickType.SHIFT_LEFT) {
                    boolean nextState = !maceManager.isGlobalForceEnabled();
                    maceManager.setGlobalForceEnabled(nextState);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, nextState ? 1.5f : 0.8f);
                    String statusStr = nextState
                            ? "<#a6e3a1><b>✔ ᴏɴ</b> <#6c7086>(" + maceManager.getGlobalForceSeconds() + "s)"
                            : "<#f38ba8><b>✖ ᴏꜰꜰ</b>";
                    String raw = plugin.getConfig().getString(
                            "admin-menu.messages.global-force-toggled",
                            "%prefix_admin%%c_text%Global %c_primary%<b>%type%</b> %c_text%Force %c_muted%» %status%"
                    );
                    admin.sendMessage(plugin.parseMessage(raw.replace("%type%", "Mace").replace("%status%", statusStr)));
                } else if (click == ClickType.SHIFT_RIGHT) {
                    int sec = maceManager.getGlobalForceSeconds();
                    maceManager.setDefaultSeconds(sec);
                    admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
                    String raw = plugin.getConfig().getString(
                            "admin-menu.messages.default-updated",
                            "%prefix_admin%%c_text%Default %c_primary%<b>%type%</b> %c_text%cooldown set to %c_primary%<b>%seconds%s</b>%c_text%."
                    );
                    admin.sendMessage(plugin.parseMessage(raw.replace("%type%", "Mace").replace("%seconds%", String.valueOf(sec))));
                } else if (click.isRightClick()) {
                    maceManager.cycleGlobalForceSeconds(false);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                } else {
                    maceManager.cycleGlobalForceSeconds(true);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                }
                menuManager.refreshGlobalDetailMenu(admin);
            }

            case "GLOBAL_COMBAT" -> {
                if (click == ClickType.SHIFT_LEFT) {
                    boolean nextState = !combatManager.isGlobalForceEnabled();
                    combatManager.setGlobalForceEnabled(nextState);
                    admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, nextState ? 1.5f : 0.8f);
                    String statusStr = nextState
                            ? "<#a6e3a1><b>✔ ᴏɴ</b> <#6c7086>(" + combatManager.getGlobalForceSeconds() + "s)"
                            : "<#f38ba8><b>✖ ᴏꜰꜰ</b>";
                    String raw = plugin.getConfig().getString(
                            "admin-menu.messages.global-force-toggled",
                            "%prefix_admin%%c_text%Global %c_primary%<b>%type%</b> %c_text%Force %c_muted%» %status%"
                    );
                    admin.sendMessage(plugin.parseMessage(raw.replace("%type%", "Combat Tag").replace("%status%", statusStr)));
                } else if (click == ClickType.SHIFT_RIGHT) {
                    int sec = combatManager.getGlobalForceSeconds();
                    combatManager.setDefaultSeconds(sec);
                    admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
                    String raw = plugin.getConfig().getString(
                            "admin-menu.messages.default-updated",
                            "%prefix_admin%%c_text%Default %c_primary%<b>%type%</b> %c_text%cooldown set to %c_primary%<b>%seconds%s</b>%c_text%."
                    );
                    admin.sendMessage(plugin.parseMessage(raw.replace("%type%", "Combat Tag").replace("%seconds%", String.valueOf(sec))));
                } else if (click.isRightClick()) {
                    combatManager.cycleGlobalForceSeconds(false);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                } else {
                    combatManager.cycleGlobalForceSeconds(true);
                    admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                }
                menuManager.refreshGlobalDetailMenu(admin);
            }

            case "GLOBAL_INTERRUPT" -> {
                boolean nextState = !combatManager.isAntiInterruptGlobal();
                combatManager.setAntiInterruptGlobal(nextState);
                admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, nextState ? 1.5f : 0.8f);
                String statusStr = nextState ? "<#a6e3a1><b>✔ ᴇɴᴀʙʟᴇᴅ</b>" : "<#f38ba8><b>✖ ᴅɪsᴀʙʟᴇᴅ</b>";
                String raw = plugin.getConfig().getString(
                        "admin-menu.messages.global-force-toggled",
                        "%prefix_admin%%c_text%Global %c_primary%<b>%type%</b> %c_text%Force %c_muted%» %status%"
                );
                admin.sendMessage(plugin.parseMessage(raw.replace("%type%", "Anti-Interrupt").replace("%status%", statusStr)));
                menuManager.refreshGlobalDetailMenu(admin);
            }

            case "GLOBAL_BACK" -> {
                menuManager.openAdminSettingMenu(admin, 0);
                admin.playSound(admin.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            }
        }
    }

    private void handleTrainingMenuClick(Player player, int slot, ClickType click) {
        if (plugin.getConfig().getBoolean("training.disable-in-combat", true) && FoCombatTagHook.isTagged(player)) {
            plugin.getTrainingManager().disableTrainingOnCombat(player);
            return;
        }

        // Slot 4: Master Switch
        if (slot == 4) {
            boolean newState = plugin.getTrainingManager().toggleMaster(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, newState ? 1.5f : 0.8f);
            plugin.getTrainingManager().refreshTrainingMenu(player);

            String statusStr = newState
                    ? plugin.getConfig().getString("training.messages.status-enabled", "%c_success%<b>✔ ᴇɴᴀʙʟᴇᴅ</b>")
                    : plugin.getConfig().getString("training.messages.status-disabled", "%c_error%<b>✖ ᴅɪsᴀʙʟᴇᴅ</b>");
            String raw = plugin.getConfig().getString(
                    "training.messages.toggled",
                    "%prefix_info%%c_text%Training Mode %c_muted%» %status%"
            );
            player.sendMessage(plugin.parseMessage(raw.replace("%status%", statusStr)));
            return;
        }

        // Slot 40: Back to main Settings menu
        if (slot == 40) {
            plugin.getSettingMenuManager().openSettingMenu(player);
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            return;
        }

        // Slots 10..16: Training Options
        TrainingOption option = TrainingOption.fromSlot(slot);
        if (option != null) {
            boolean newState = plugin.getTrainingManager().toggleOption(player.getUniqueId(), option);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, newState ? 1.2f : 0.9f);
            plugin.getTrainingManager().refreshTrainingMenu(player);

            String statusStr = newState
                    ? plugin.getConfig().getString("training.messages.status-enabled", "%c_success%<b>✔ ᴇɴᴀʙʟᴇᴅ</b>")
                    : plugin.getConfig().getString("training.messages.status-disabled", "%c_error%<b>✖ ᴅɪsᴀʙʟᴇᴅ</b>");
            String raw = plugin.getConfig().getString(
                    "training.messages.option-toggled",
                    "%prefix_info%%c_primary%<b>%option%</b> %c_muted%» %status%"
            );
            player.sendMessage(plugin.parseMessage(raw.replace("%option%", option.getDisplayName()).replace("%status%", statusStr)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder topHolder = event.getView().getTopInventory().getHolder();
        if (topHolder instanceof SettingMenuHolder
                || topHolder instanceof AdminSettingMenuHolder
                || topHolder instanceof WorldDetailMenuHolder
                || topHolder instanceof GlobalDetailMenuHolder
                || topHolder instanceof TrainingMenuHolder) {
            event.setCancelled(true);
        }
    }
}
