package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RegearListener implements Listener {

    private final VCarterUtils plugin;

    // Luu toa do cac Shulker Regear da dat tren san
    private final Set<Location> regearShulkerLocations = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // Theo doi nguoi choi vua go lenh /regear
    private final Map<UUID, Long> recentRegearCommands = new ConcurrentHashMap<>();

    public RegearListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().trim().toLowerCase();
        if (msg.equals("/regear") || msg.startsWith("/regear ") ||
            msg.equals("/rg") || msg.startsWith("/rg ") ||
            msg.endsWith(":regear") || msg.endsWith(":rg")) {
            recentRegearCommands.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!item.getType().name().endsWith("SHULKER_BOX")) {
            return;
        }

        Player player = event.getPlayer();
        boolean isRegearShulker = false;

        // 1. Kiem tra theo DisplayName
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String name = item.getItemMeta().getDisplayName().toLowerCase();
            if (name.contains("regear")) {
                isRegearShulker = true;
            }
        }

        // 2. Kiem tra xem ben trong Shulker Item co chua san SHULKER_SHELL cua PPK khong
        if (!isRegearShulker && item.getItemMeta() instanceof BlockStateMeta bsm) {
            if (bsm.getBlockState() instanceof ShulkerBox sb) {
                if (sb.getInventory().contains(Material.SHULKER_SHELL)) {
                    isRegearShulker = true;
                }
            }
        }

        // 3. Kiem tra nguoi choi vua go lenh regear trong vong 60 giay
        Long cmdTime = recentRegearCommands.get(player.getUniqueId());
        if (!isRegearShulker && cmdTime != null && (System.currentTimeMillis() - cmdTime < 60000L)) {
            isRegearShulker = true;
        }

        if (isRegearShulker) {
            Block block = event.getBlockPlaced();
            regearShulkerLocations.add(block.getLocation());
            block.setMetadata("VCarter_Regear", new FixedMetadataValue(plugin, true));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Inventory inv = event.getInventory();
        if (inv.getType() != InventoryType.SHULKER_BOX) {
            return;
        }

        Location loc = inv.getLocation();
        boolean isRegear = (loc != null && regearShulkerLocations.contains(loc)) ||
                           (loc != null && loc.getBlock().hasMetadata("VCarter_Regear"));

        int shellSlot = -1;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.getType() == Material.SHULKER_SHELL) {
                shellSlot = i;
                isRegear = true;
                break;
            }
        }

        if (!isRegear) {
            return;
        }

        // Kich hoat click len Shulker Shell de PerPlayerKit thuc hien regear
        if (shellSlot != -1) {
            InventoryView view = event.getView();
            InventoryClickEvent clickEvent = new InventoryClickEvent(
                    view,
                    InventoryType.SlotType.CONTAINER,
                    shellSlot,
                    ClickType.LEFT,
                    InventoryAction.PICKUP_ALL
            );
            Bukkit.getPluginManager().callEvent(clickEvent);
        }

        // Dong inventory ngay lap tuc de khong chan man hinh cua nguoi choi va don sach hop Shulker
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.closeInventory();
            }
            if (loc != null) {
                regearShulkerLocations.remove(loc);
                Block b = loc.getBlock();
                if (b.getType().name().endsWith("SHULKER_BOX")) {
                    b.removeMetadata("VCarter_Regear", plugin);
                    b.setType(Material.AIR);
                    loc.getWorld().playSound(loc, Sound.BLOCK_SHULKER_BOX_CLOSE, 1.0f, 1.2f);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        regearShulkerLocations.remove(loc);
        event.getBlock().removeMetadata("VCarter_Regear", plugin);
    }

    public void clearAll() {
        regearShulkerLocations.clear();
        recentRegearCommands.clear();
    }
}
