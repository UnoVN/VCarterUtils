package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.TotemManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class TotemListener implements Listener {

    private final VCarterUtils plugin;

    public TotemListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    /**
     * Chặn kích hoạt Totem nếu đã hết lượt trong lượt kit này
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        TotemManager manager = plugin.getTotemManager();
        if (!manager.isEnabled() || manager.isWorldBlacklisted(player.getWorld())) {
            return;
        }

        UUID uuid = player.getUniqueId();
        int used = manager.getActivations(uuid);
        int max = manager.getMaxActivations();

        // Neu da kich hoat du so lan cho phep -> Huy hoi sinh, nguoi choi chet binh thuong
        if (used >= max) {
            event.setCancelled(true);
            manager.sendActivationLimitMessage(player);
            return;
        }

        // Kich hoat lan dau thanh cong -> ghi nhan 1 lan su dung
        manager.recordActivation(uuid);
    }

    /**
     * Chặn nhặt thêm Totem nếu trong người đã có đủ số lượng cho phép
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        TotemManager manager = plugin.getTotemManager();
        if (!manager.isEnabled() || manager.isWorldBlacklisted(player.getWorld())) {
            return;
        }

        ItemStack item = event.getItem().getItemStack();
        if (item.getType() != Material.TOTEM_OF_UNDYING) {
            return;
        }

        int currentCount = manager.countTotems(player);
        int max = manager.getMaxInInventory();

        if (currentCount >= max) {
            event.setCancelled(true);
            manager.sendInventoryLimitMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleTotemLimitCheck(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleTotemLimitCheck(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            scheduleTotemLimitCheck(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getTotemManager().resetActivations(event.getPlayer().getUniqueId());
    }

    private void scheduleTotemLimitCheck(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.getTotemManager().enforceInventoryLimit(player);
            }
        });
    }
}
