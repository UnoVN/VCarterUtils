package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RegearListener implements Listener {

    private final VCarterUtils plugin;
    private final NamespacedKey regearKey;
    private final MiniMessage miniMessage;

    // Danh sach toa do cac Shulker Regear duoc dat tren san
    private final Set<Location> activeShulkerBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // Chặn loop lệnh khi plugin chạy lệnh regear ngầm
    private final Set<UUID> bypassCommandFilter = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public RegearListener(VCarterUtils plugin) {
        this.plugin = plugin;
        this.regearKey = new NamespacedKey(plugin, "vcarter_regear_shulker");
        this.miniMessage = MiniMessage.miniMessage();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRegearCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (bypassCommandFilter.contains(player.getUniqueId())) {
            return;
        }

        String raw = event.getMessage().trim().toLowerCase();
        String cmd = raw.split(" ")[0];

        if (cmd.equals("/regear") || cmd.equals("/rg") ||
            cmd.endsWith(":regear") || cmd.endsWith(":rg")) {

            event.setCancelled(true);

            // 1. Kiem tra xem nguoi choi da co Shulker Regear trong nguoi chua
            if (hasRegearShulker(player)) {
                String msgRaw = plugin.getConfig().getString(
                        "regear.messages.already-have-shulker",
                        "<glyph:warning_blue> <#f38ba8>You already have a <#89dceb>Regear Shulker<#f38ba8> in your inventory!"
                );
                player.sendMessage(format(player, msgRaw));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // 2. Kiem tra kho do co bi day khong
            if (player.getInventory().firstEmpty() == -1) {
                String msgRaw = plugin.getConfig().getString(
                        "regear.messages.inventory-full",
                        "<glyph:warning_blue> <#f38ba8>Your inventory is full! <#a6adc8>Please free up at least 1 slot to receive the <#89dceb>Regear Shulker<#a6adc8>."
                );
                player.sendMessage(format(player, msgRaw));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // 3. Trao vat pham Shulker Regear va gui thong bao
            player.getInventory().addItem(createRegearShulkerItem());
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);

            String msgRaw = plugin.getConfig().getString(
                    "regear.messages.received-shulker",
                    "<glyph:warning_blue> <#a6e3a1>Received <#89dceb><b>Shulker Regear</b><#a6e3a1>! Place it down and <#f9e2af><b>Right-Click</b><#a6e3a1> to restock."
            );
            player.sendMessage(format(player, msgRaw));
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

        activeShulkerBlocks.add(loc);
        block.setMetadata("VCarter_Regear_Block", new FixedMetadataValue(plugin, true));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
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

        // 1. Chặn mở giao diện Shulker
        event.setCancelled(true);

        Player player = event.getPlayer();

        // 2. Dọn sạch khối Shulker trên mặt đất
        activeShulkerBlocks.remove(loc);
        block.removeMetadata("VCarter_Regear_Block", plugin);
        block.setType(Material.AIR);
        loc.getWorld().playSound(loc, Sound.BLOCK_SHULKER_BOX_CLOSE, 1.0f, 1.2f);

        // 3. Thực thi lệnh Regear ngầm qua PerPlayerKit
        executePerPlayerKitRegear(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();

        if (activeShulkerBlocks.contains(loc) || block.hasMetadata("VCarter_Regear_Block")) {
            activeShulkerBlocks.remove(loc);
            block.removeMetadata("VCarter_Regear_Block", plugin);

            // Hoàn trả đúng vật phẩm Shulker Regear có gắn thẻ PDC
            event.setDropItems(false);
            loc.getWorld().dropItemNaturally(loc, createRegearShulkerItem());
        }
    }

    private void executePerPlayerKitRegear(Player player) {
        bypassCommandFilter.add(player.getUniqueId());
        try {
            boolean success = player.performCommand("perplayerkit:regear");
            if (!success) {
                player.performCommand("regear");
            }
        } finally {
            bypassCommandFilter.remove(player.getUniqueId());
        }
    }

    private boolean hasRegearShulker(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isRegearItem(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRegearItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(regearKey, PersistentDataType.BYTE);
    }

    private ItemStack createRegearShulkerItem() {
        ItemStack item = new ItemStack(Material.CYAN_SHULKER_BOX, 1);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String nameRaw = plugin.getConfig().getString(
                    "regear.item.name",
                    "<#89dceb><b><!italic>Shulker Regear</b>"
            );
            meta.displayName(miniMessage.deserialize(nameRaw));

            List<String> loreRaw = plugin.getConfig().getStringList("regear.item.lore");
            if (loreRaw.isEmpty()) {
                loreRaw = List.of(
                        "<#a6adc8><!italic>Place on the ground and <#f9e2af><b>Right-Click</b><#a6adc8> to restock.",
                        "<#6c7086><!italic>Automatically disappears after use."
                );
            }

            List<Component> loreComponents = new ArrayList<>();
            for (String line : loreRaw) {
                loreComponents.add(miniMessage.deserialize(line));
            }
            meta.lore(loreComponents);

            // Gắn thẻ PDC để nhận diện chính xác
            meta.getPersistentDataContainer().set(regearKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }

        return item;
    }

    private Component format(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
        return miniMessage.deserialize(text);
    }

    public void clearAll() {
        activeShulkerBlocks.clear();
        bypassCommandFilter.clear();
    }
}
