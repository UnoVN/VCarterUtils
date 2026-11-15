package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RegearListener implements Listener {

    private final VCarterUtils plugin;
    private final Set<Location> activeRegearBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public RegearListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.getRegearManager().recordDamage(player.getUniqueId());
        }
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent edbe) {
            if (edbe.getDamager() instanceof Player damager) {
                plugin.getRegearManager().recordDamage(damager.getUniqueId());
            }
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

        // Check immediately at placement if regear can proceed. If not, cancel block placement,
        // send cooldown/combat message, and preserve item in hand without phantom placement.
        if (!plugin.getRegearManager().canRegear(player, true)) {
            event.setCancelled(true);
            return;
        }

        EquipmentSlot hand = event.getHand();
        Block block = event.getBlockPlaced();
        Location loc = block.getLocation();

        block.setMetadata("VCarter_Regear_Block", new FixedMetadataValue(plugin, true));
        activeRegearBlocks.add(loc);

        Bukkit.getScheduler().runTask(plugin, () -> {
            activeRegearBlocks.remove(loc);
            Block liveBlock = loc.getBlock();

            if (block.hasMetadata("VCarter_Regear_Block")) {
                block.removeMetadata("VCarter_Regear_Block", plugin);
            }
            if (liveBlock.hasMetadata("VCarter_Regear_Block")) {
                liveBlock.removeMetadata("VCarter_Regear_Block", plugin);
            }
            if (liveBlock.getType().name().endsWith("SHULKER_BOX") || block.getType().name().endsWith("SHULKER_BOX")) {
                liveBlock.setType(Material.AIR, false);
                if (loc.getWorld() != null) {
                    loc.getWorld().playSound(loc, Sound.BLOCK_SHULKER_BOX_CLOSE, 1.0f, 1.2f);
                }
            }

            if (player.isOnline()) {
                player.sendBlockChange(loc, Material.AIR.createBlockData());

                // Ensure the placed Regear Shulker is consumed from hand/inventory before restocking
                ItemStack inHand = player.getInventory().getItem(hand);
                if (isRegearItem(inHand)) {
                    player.getInventory().setItem(hand, null);
                }
                plugin.getRegearManager().removeAllRegearItems(player);

                boolean success = plugin.getRegearManager().performRegear(player);
                if (success) {
                    plugin.getRegearManager().removeAllRegearItems(player);
                    player.updateInventory();
                } else {
                    if (!hasRegearShulker(player)) {
                        player.getInventory().addItem(createRegearShulkerItem());
                    }
                    player.updateInventory();
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
            activeRegearBlocks.remove(block.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> b.hasMetadata("VCarter_Regear_Block"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> b.hasMetadata("VCarter_Regear_Block"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (b.hasMetadata("VCarter_Regear_Block")) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (b.hasMetadata("VCarter_Regear_Block")) {
                event.setCancelled(true);
                return;
            }
        }
    }

    public void clearAll() {
        for (Location loc : activeRegearBlocks) {
            if (loc != null && loc.getWorld() != null) {
                Block b = loc.getBlock();
                if (b.hasMetadata("VCarter_Regear_Block")) {
                    b.removeMetadata("VCarter_Regear_Block", plugin);
                }
                if (b.getType().name().endsWith("SHULKER_BOX")) {
                    b.setType(Material.AIR, false);
                }
            }
        }
        activeRegearBlocks.clear();
    }

    private boolean hasRegearShulker(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isRegearItem(item)) {
                return true;
            }
        }
        return isRegearItem(player.getInventory().getItemInOffHand())
                || isRegearItem(player.getItemOnCursor());
    }

    public boolean isRegearItem(ItemStack item) {
        return plugin.getRegearManager().isRegearItem(item);
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
            meta.displayName(plugin.parseMessage(nameRaw));

            List<String> loreRaw = plugin.getConfig().getStringList("regear.item.lore");
            if (loreRaw.isEmpty()) {
                loreRaw = List.of(
                        "<!italic><white><glyph:right_click></white> <#a6adc8>Place to restock inventory",
                        "<!italic><#6c7086>Auto-removes after placement"
                );
            }

            List<Component> loreComponents = new ArrayList<>();
            for (String line : loreRaw) {
                loreComponents.add(plugin.parseMessage(line));
            }
            meta.lore(loreComponents);

            meta.getPersistentDataContainer().set(plugin.getRegearManager().getRegearKey(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }

        return item;
    }

    private void sendMsg(Player player, String configPath) {
        String raw = plugin.getConfig().getString(configPath);
        if (raw != null && !raw.isEmpty()) {
            player.sendMessage(plugin.parseMessage(raw));
        }
    }
}
