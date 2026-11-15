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
     * Blocks Totem activation if the player has exceeded the allowed activation limit for this kit.
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

        // Training mode hook: If training mode is active, bypass anti-totem activation limits
        if (plugin.getTrainingManager() != null && plugin.getTrainingManager().isPlayerEnabled(player.getUniqueId())) {
            return;
        }

        UUID uuid = player.getUniqueId();
        int used = manager.getActivations(uuid);
        int max = manager.getMaxActivations();

        // If activation limit reached, cancel resurrection so the player dies normally
        if (used >= max) {
            event.setCancelled(true);
            manager.sendActivationLimitMessage(player);
            return;
        }

        // Successfully activated: record 1 activation
        manager.recordActivation(uuid);
    }

    /**
     * Blocks picking up extra Totems if the player already carries the maximum allowed.
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

        // Training mode hook: If training mode is active, do not restrict totem pickup
        if (plugin.getTrainingManager() != null && plugin.getTrainingManager().isPlayerEnabled(player.getUniqueId())) {
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
        if (plugin.getTrainingManager() != null && plugin.getTrainingManager().isPlayerEnabled(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.getTotemManager().enforceInventoryLimit(player);
            }
        });
    }
}
