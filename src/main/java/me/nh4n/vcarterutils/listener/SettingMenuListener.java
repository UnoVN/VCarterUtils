package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.PearlManager;
import me.nh4n.vcarterutils.manager.SettingMenuManager;
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

public class SettingMenuListener implements Listener {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    public SettingMenuListener(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SettingMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        int slot = event.getSlot();
        MenuItemAction action = holder.getSlotAction(slot);
        if (action == null) {
            return;
        }

        String type = action.type();
        PearlManager pearlManager = plugin.getPearlManager();
        SettingMenuManager menuManager = plugin.getSettingMenuManager();

        if (type.equalsIgnoreCase("PEARL_COOLDOWN")) {
            ClickType click = event.getClick();
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SettingMenuHolder) {
            event.setCancelled(true);
        }
    }
}
