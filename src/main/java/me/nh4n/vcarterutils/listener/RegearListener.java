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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class RegearListener implements Listener {

    private final VCarterUtils plugin;
    private final NamespacedKey regearKey;
    private final MiniMessage miniMessage;

    public RegearListener(VCarterUtils plugin) {
        this.plugin = plugin;
        this.regearKey = new NamespacedKey(plugin, "vcarter_regear_shulker");
        this.miniMessage = MiniMessage.miniMessage();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.getRegearManager().recordDamage(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRegearCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().trim().toLowerCase();
        String cmd = raw.split(" ")[0];

        if (cmd.equals("/regear") || cmd.equals("/rg") ||
            cmd.endsWith(":regear") || cmd.endsWith(":rg")) {

            event.setCancelled(true);
            Player player = event.getPlayer();

            if (hasRegearShulker(player)) {
                sendMsg(player, "regear.messages.already-have-shulker");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            if (player.getInventory().firstEmpty() == -1) {
                sendMsg(player, "regear.messages.inventory-full");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            player.getInventory().addItem(createRegearShulkerItem());
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
            sendMsg(player, "regear.messages.received-shulker");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!isRegearItem(item)) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        Location loc = block.getLocation();

        block.setMetadata("VCarter_Regear_Block", new FixedMetadataValue(plugin, true));

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (block.hasMetadata("VCarter_Regear_Block") || block.getType().name().endsWith("SHULKER_BOX")) {
                block.removeMetadata("VCarter_Regear_Block", plugin);
                block.setType(Material.AIR);
                loc.getWorld().playSound(loc, Sound.BLOCK_SHULKER_BOX_CLOSE, 1.0f, 1.2f);
            }

            if (player.isOnline()) {
                boolean success = plugin.getRegearManager().performRegear(player);
                if (!success) {
                    player.getInventory().addItem(createRegearShulkerItem());
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.hasMetadata("VCarter_Regear_Block")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.hasMetadata("VCarter_Regear_Block")) {
            event.setDropItems(false);
            block.removeMetadata("VCarter_Regear_Block", plugin);
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
        String matName = plugin.getConfig().getString("regear.item.material", "CYAN_SHULKER_BOX");
        Material mat = Material.matchMaterial(matName != null ? matName : "CYAN_SHULKER_BOX");
        if (mat == null || !mat.name().endsWith("SHULKER_BOX")) {
            mat = Material.CYAN_SHULKER_BOX;
        }

        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String nameRaw = plugin.getConfig().getString(
                    "regear.item.name",
                    "<!italic><#89dceb><b>Shulker Regear</b>"
            );
            meta.displayName(miniMessage.deserialize(nameRaw));

            List<String> loreRaw = plugin.getConfig().getStringList("regear.item.lore");
            if (loreRaw.isEmpty()) {
                loreRaw = List.of(
                        "<!italic><white><glyph:right_click></white> <#a6adc8>Place to restock inventory",
                        "<!italic><#6c7086>Auto-removes after placement"
                );
            }

            List<Component> loreComponents = new ArrayList<>();
            for (String line : loreRaw) {
                loreComponents.add(miniMessage.deserialize(line));
            }
            meta.lore(loreComponents);

            meta.getPersistentDataContainer().set(regearKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }

        return item;
    }

    private void sendMsg(Player player, String configPath) {
        String raw = plugin.getConfig().getString(configPath);
        if (raw != null && !raw.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(raw));
        }
    }
}
