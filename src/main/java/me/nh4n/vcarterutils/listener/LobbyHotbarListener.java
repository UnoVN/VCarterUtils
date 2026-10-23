package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.LobbyHotbarManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LobbyHotbarListener implements Listener {

    private final VCarterUtils plugin;
    private final Map<UUID, Long> lastInteractTimes = new ConcurrentHashMap<>();

    public LobbyHotbarListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    /**
     * Khi nguoi choi tu tran: Danh dau dang respawn va xoa savedHotbar cu
     * de tranh PlayerChangedWorldEvent o Tick 0 luu nham hotbar rong truoc khi PerPlayerKit nap kit!
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        plugin.getLobbyHotbarManager().markRespawning(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        LobbyHotbarManager manager = plugin.getLobbyHotbarManager();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (manager.isLobbyWorld(player.getWorld()) && manager.isEnabled() && manager.isPlayerEnabled(player.getUniqueId())) {
                manager.applyLobbyHotbar(player);
            } else if (manager.hasSavedHotbar(player.getUniqueId()) || manager.hasAnyLobbyItem(player)) {
                manager.restorePlayerHotbar(player);
            }
        }, 4L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        LobbyHotbarManager manager = plugin.getLobbyHotbarManager();
        World currentWorld = player.getWorld();

        if (manager.isLobbyWorld(currentWorld)) {
            // Neu nguoi choi chuyen world do vua chet & hoi sinh ve Lobby:
            // AutoPotListener se xu ly o Tick 6 (sau khi PerPlayerKit nap xong kit).
            if (manager.isRespawning(uuid)) {
                return;
            }
            if (manager.isEnabled() && manager.isPlayerEnabled(uuid)) {
                manager.applyLobbyHotbar(player);
            }
        } else {
            manager.unmarkRespawning(uuid);
            manager.restorePlayerHotbar(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        LobbyHotbarManager manager = plugin.getLobbyHotbarManager();
        if (!manager.isLobbyItem(item)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        long now = System.currentTimeMillis();
        Long last = lastInteractTimes.get(player.getUniqueId());
        if (last != null && (now - last < 250L)) {
            return;
        }
        lastInteractTimes.put(player.getUniqueId(), now);

        String cmd = manager.getLobbyItemCommand(item);
        if (cmd != null && !cmd.trim().isEmpty()) {
            String cleanCmd = cmd.trim().startsWith("/") ? cmd.trim().substring(1) : cmd.trim();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            player.performCommand(cleanCmd.replace("%player%", player.getName()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        LobbyHotbarManager manager = plugin.getLobbyHotbarManager();
        if (manager.isLobbyItem(event.getCurrentItem()) || manager.isLobbyItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player player) {
            ItemStack swapped = player.getInventory().getItem(event.getHotbarButton());
            if (manager.isLobbyItem(swapped)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (plugin.getLobbyHotbarManager().isLobbyItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getLobbyHotbarManager().isLobbyItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        LobbyHotbarManager manager = plugin.getLobbyHotbarManager();
        if (manager.isLobbyItem(event.getMainHandItem()) || manager.isLobbyItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getLobbyHotbarManager().isLobbyItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastInteractTimes.remove(uuid);
        plugin.getLobbyHotbarManager().unmarkRespawning(uuid);
    }
}
