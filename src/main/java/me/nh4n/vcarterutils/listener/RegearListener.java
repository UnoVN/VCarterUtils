package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RegearListener implements Listener {

    private final VCarterUtils plugin;
    private final NamespacedKey regearKey;

    // Danh sach toa do cac Shulker Regear duoc dat tren mat dat
    private final Set<Location> activeShulkerBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // Chặn vòng lặp vô hạn khi plugin chủ động phát lệnh regear
    private final Set<UUID> bypassCommandFilter = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Màu sắc thông báo chuẩn giao diện PvP
    private static final TextColor PREFIX_COLOR = TextColor.fromHexString("#e64553");
    private static final TextColor INFO_COLOR   = TextColor.fromHexString("#a6d189");
    private static final TextColor ACCENT_COLOR = TextColor.fromHexString("#04a5e5");

    public RegearListener(VCarterUtils plugin) {
        this.plugin = plugin;
        this.regearKey = new NamespacedKey(plugin, "vcarter_regear_shulker");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRegearCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // Neu lenh do chinh plugin phat ra thi cho phep PerPlayerKit xu ly
        if (bypassCommandFilter.contains(player.getUniqueId())) {
            return;
        }

        String raw = event.getMessage().trim().toLowerCase();
        String cmd = raw.split(" ")[0];

        // Chặn lệnh /regear hoặc /rg từ người chơi
        if (cmd.equals("/regear") || cmd.equals("/rg") ||
            cmd.endsWith(":regear") || cmd.endsWith(":rg")) {

            event.setCancelled(true);

            // 1. Kiểm tra người chơi đã có Shulker Regear trong túi đồ chưa
            if (hasRegearShulker(player)) {
                player.sendMessage(Component.text()
                        .append(Component.text("[VCarter] ", PREFIX_COLOR, TextDecoration.BOLD))
                        .append(Component.text("Bạn đã có sẵn Shulker Regear trong kho đồ!", NamedTextColor.RED))
                        .build());
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // 2. Kiểm tra kho đồ có bị đầy không
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(Component.text()
                        .append(Component.text("[VCarter] ", PREFIX_COLOR, TextDecoration.BOLD))
                        .append(Component.text("Kho đồ đã đầy! Hãy dọn trống ít nhất 1 ô để nhận Shulker Regear.", NamedTextColor.RED))
                        .build());
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // 3. Trao Shulker Regear và gửi thông báo
            player.getInventory().addItem(createRegearShulkerItem());
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);

            player.sendMessage(Component.text()
                    .append(Component.text("[VCarter] ", PREFIX_COLOR, TextDecoration.BOLD))
                    .append(Component.text("Đã nhận ", INFO_COLOR))
                    .append(Component.text("Shulker Regear", ACCENT_COLOR, TextDecoration.BOLD))
                    .append(Component.text("! Đặt xuống đất và Chuột Phải để nạp lại đồ.", INFO_COLOR))
                    .build());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!isRegearItem(item)) {
            return;
        }

        Block block = event.getBlockPlaced();
        Location loc = block.getLocation();

        // Ghi nhận khối Shulker này là Regear Box
        activeShulkerBlocks.add(loc);
        block.setMetadata("VCarter_Regear_Block", new FixedMetadataValue(plugin, true));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Chỉ bắt hành động Chuột Phải vào khối Block
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !block.getType().name().endsWith("SHULKER_BOX")) {
            return;
        }

        Location loc = block.getLocation();
        if (!activeShulkerBlocks.contains(loc) && !block.hasMetadata("VCarter_Regear_Block")) {
            return;
        }

        // 1. Chặn mở giao diện rương hoàn toàn
        event.setCancelled(true);

        Player player = event.getPlayer();

        // 2. Dọn sạch khối Shulker trên sàn đấu ngay lập tức
        activeShulkerBlocks.remove(loc);
        block.removeMetadata("VCarter_Regear_Block", plugin);
        block.setType(Material.AIR);
        loc.getWorld().playSound(loc, Sound.BLOCK_SHULKER_BOX_CLOSE, 1.0f, 1.2f);

        // 3. Kích hoạt tính năng Regear từ PerPlayerKit ở chế độ Command Mode
        executePerPlayerKitRegear(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();

        if (activeShulkerBlocks.contains(loc) || block.hasMetadata("VCarter_Regear_Block")) {
            activeShulkerBlocks.remove(loc);
            block.removeMetadata("VCarter_Regear_Block", plugin);

            // Tránh rớt Shulker thường, hoàn trả lại đúng Shulker Regear có gắn thẻ PDC
            event.setDropItems(false);
            loc.getWorld().dropItemNaturally(loc, createRegearShulkerItem());
        }
    }

    /**
     * Bắn lệnh regear ngầm sang PerPlayerKit mà không bị bộ lọc command chặn lại
     */
    private void executePerPlayerKitRegear(Player player) {
        bypassCommandFilter.add(player.getUniqueId());
        try {
            // PerPlayerKit đang ở command mode sẽ tự nạp đầy kho đồ theo kit
            boolean success = player.performCommand("perplayerkit:regear");
            if (!success) {
                player.performCommand("regear");
            }
        } finally {
            bypassCommandFilter.remove(player.getUniqueId());
        }
    }

    /**
     * Kiểm tra người chơi có đang giữ Shulker Regear hay không
     */
    private boolean hasRegearShulker(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isRegearItem(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nhận diện Shulker Regear thông qua PersistentDataContainer
     */
    private boolean isRegearItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(regearKey, PersistentDataType.BYTE);
    }

    /**
     * Khởi tạo vật phẩm Shulker Regear chuẩn NBT/PDC
     */
    private ItemStack createRegearShulkerItem() {
        ItemStack item = new ItemStack(Material.CYAN_SHULKER_BOX, 1);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("Shulker Regear", ACCENT_COLOR, TextDecoration.BOLD));

            meta.lore(List.of(
                    Component.text("Đặt xuống đất và ", NamedTextColor.GRAY)
                            .append(Component.text("Chuột Phải", NamedTextColor.YELLOW, TextDecoration.BOLD))
                            .append(Component.text(" để nạp lại đồ.", NamedTextColor.GRAY)),
                    Component.text("Tự động biến mất sau khi kích hoạt.", NamedTextColor.DARK_GRAY)
            ));

            // Đóng dấu định danh để chống làm giả hoặc nhận nhầm Shulker thường
            meta.getPersistentDataContainer().set(regearKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }

        return item;
    }

    public void clearAll() {
        activeShulkerBlocks.clear();
        bypassCommandFilter.clear();
    }
}
