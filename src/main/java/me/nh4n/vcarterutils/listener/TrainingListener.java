package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.hook.FoCombatTagHook;
import me.nh4n.vcarterutils.manager.TrainingManager;
import me.nh4n.vcarterutils.manager.TrainingOption;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TrainingListener implements Listener {

    private final VCarterUtils plugin;
    private final TrainingManager trainingManager;

    public TrainingListener(VCarterUtils plugin, TrainingManager trainingManager) {
        this.plugin = plugin;
        this.trainingManager = trainingManager;
    }

    /**
     * Infinite blocks & rails: placing blocks or rails does not consume items and refuels hotbar.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        if (plugin.getRegearManager().isRegearItem(inHand)
                || plugin.getLobbyHotbarManager().isLobbyItem(inHand)
                || event.getBlockPlaced().hasMetadata("VCarter_Regear_Block")) {
            return;
        }

        Player player = event.getPlayer();
        if (!trainingManager.isOptionEffective(player, TrainingOption.INFINITE_BLOCKS)
                && !trainingManager.isOptionEffective(player, TrainingOption.AUTO_REFILL)) {
            return;
        }

        EquipmentSlot hand = event.getHand();
        final int heldSlot = (hand == EquipmentSlot.HAND) ? player.getInventory().getHeldItemSlot() : -1;
        ItemStack placedItem = inHand.clone();
        int maxStack = placedItem.getMaxStackSize();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            ItemStack current = (heldSlot >= 0)
                    ? player.getInventory().getItem(heldSlot)
                    : player.getInventory().getItem(hand);
            int refillAmount = (maxStack > 1 && trainingManager.isOptionEffective(player, TrainingOption.AUTO_REFILL))
                    ? maxStack
                    : Math.max(1, placedItem.getAmount());

            if (current == null || current.getType() == Material.AIR) {
                placedItem.setAmount(refillAmount);
                if (heldSlot >= 0) player.getInventory().setItem(heldSlot, placedItem);
                else player.getInventory().setItem(hand, placedItem);
            } else if (current.isSimilar(placedItem)) {
                current.setAmount(refillAmount);
                if (heldSlot >= 0) player.getInventory().setItem(heldSlot, current);
                else player.getInventory().setItem(hand, current);
            }
            autoRefillHotbar(player);
            player.updateInventory();
        });
    }

    /**
     * Crossbow auto-reload & infinite ammo:
     * Crossbow shot does not lose reload (remains loaded with projectile).
     * Bow and Crossbow do not consume arrows / fireworks from inventory.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!trainingManager.isOptionEffective(player, TrainingOption.CROSSBOW_RELOAD)) return;

        // Infinite ammo: do not consume ammunition from inventory
        event.setConsumeItem(false);

        ItemStack bow = event.getBow();
        if (bow != null && bow.getType() == Material.CROSSBOW) {
            List<ItemStack> chargedProjectiles = new ArrayList<>();
            if (bow.getItemMeta() instanceof CrossbowMeta meta && meta.hasChargedProjectiles()) {
                for (ItemStack p : meta.getChargedProjectiles()) {
                    if (p != null && p.getType() != Material.AIR) {
                        chargedProjectiles.add(p.clone());
                    }
                }
            }

            if (chargedProjectiles.isEmpty()) {
                ItemStack consumable = event.getConsumable();
                if (consumable != null && consumable.getType() != Material.AIR) {
                    ItemStack copy = consumable.clone();
                    copy.setAmount(1);
                    chargedProjectiles.add(copy);
                } else {
                    chargedProjectiles.add(new ItemStack(Material.ARROW, 1));
                }
            }

            final List<ItemStack> finalCharged = chargedProjectiles;
            final EquipmentSlot hand = (player.getInventory().getItemInMainHand().getType() == Material.CROSSBOW)
                    ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;

            // Re-charge crossbow on the next tick so it remains loaded immediately
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                ItemStack current = player.getInventory().getItem(hand);
                if (current != null && current.getType() == Material.CROSSBOW && current.getItemMeta() instanceof CrossbowMeta cbMeta) {
                    cbMeta.setChargedProjectiles(finalCharged);
                    current.setItemMeta(cbMeta);
                    player.getInventory().setItem(hand, current);
                    player.updateInventory();
                }
            });
        }
    }

    /**
     * Infinite consumables: eating food, golden apples, drinking potions or milk
     * does not consume item and does not leave empty bottles or buckets.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!trainingManager.isOptionEffective(player, TrainingOption.INFINITE_CONSUMABLES)) return;

        ItemStack consumed = event.getItem().clone();
        int initialAmount = consumed.getAmount();

        // Prevent vanilla bottle/bucket replacement
        event.setReplacement(consumed.clone());

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            ItemStack main = player.getInventory().getItemInMainHand();
            ItemStack off = player.getInventory().getItemInOffHand();

            if (main.isSimilar(consumed)) {
                main.setAmount(initialAmount);
                player.getInventory().setItemInMainHand(main);
            } else if (off.isSimilar(consumed)) {
                off.setAmount(initialAmount);
                player.getInventory().setItemInOffHand(off);
            } else if (main.getType() == Material.AIR && (off == null || off.getType() == Material.AIR || !off.isSimilar(consumed))) {
                consumed.setAmount(1);
                player.getInventory().setItemInMainHand(consumed);
            } else if (off.getType() == Material.AIR) {
                consumed.setAmount(1);
                player.getInventory().setItemInOffHand(consumed);
            }
            autoRefillHotbar(player);
            player.updateInventory();
        });
    }

    /**
     * Usable throwable/deployable items & TNT CARTS:
     * TNT carts on rails, pearls, splash/lingering potions, wind charges,
     * end crystals, firework rockets, snowballs, eggs, exp bottles, glowstone.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (plugin.getRegearManager().isRegearItem(item) || plugin.getLobbyHotbarManager().isLobbyItem(item)) return;

        Material mat = item.getType();
        if (!isPreservedUsableItem(mat)) return;

        // When throwing pearls in training mode, ensure combat tag is untagged immediately
        if (mat == Material.ENDER_PEARL && trainingManager.isMasterEnabled(player.getUniqueId())) {
            if (FoCombatTagHook.isTagged(player)) {
                FoCombatTagHook.untag(player);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && FoCombatTagHook.isTagged(player)) {
                    FoCombatTagHook.untag(player);
                }
            });
        }

        if (!trainingManager.isOptionEffective(player, TrainingOption.INFINITE_BLOCKS)
                && !trainingManager.isOptionEffective(player, TrainingOption.AUTO_REFILL)) {
            return;
        }

        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        final int heldSlot = (hand == EquipmentSlot.HAND) ? player.getInventory().getHeldItemSlot() : -1;
        ItemStack snapshot = item.clone();
        int initialAmount = snapshot.getAmount();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            ItemStack current = (heldSlot >= 0)
                    ? player.getInventory().getItem(heldSlot)
                    : player.getInventory().getItem(hand);
            int targetAmount = (mat.getMaxStackSize() > 1 && trainingManager.isOptionEffective(player, TrainingOption.AUTO_REFILL))
                    ? mat.getMaxStackSize()
                    : Math.max(1, initialAmount);

            if (current == null || current.getType() == Material.AIR || current.isSimilar(snapshot)) {
                snapshot.setAmount(targetAmount);
                if (heldSlot >= 0) player.getInventory().setItem(heldSlot, snapshot);
                else player.getInventory().setItem(hand, snapshot);
            } else if (current.getAmount() < initialAmount && current.getType() == snapshot.getType()) {
                current.setAmount(targetAmount);
                if (heldSlot >= 0) player.getInventory().setItem(heldSlot, current);
                else player.getInventory().setItem(hand, current);
            }
            autoRefillHotbar(player);
            player.updateInventory();
        });
    }

    /**
     * Entity placement: TNT Carts on rails, Minecarts, and End Crystals.
     * Prevents depletion and restores item to hand/hotbar immediately.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE) return;
        if (!trainingManager.isOptionEffective(player, TrainingOption.INFINITE_BLOCKS)
                && !trainingManager.isOptionEffective(player, TrainingOption.AUTO_REFILL)) {
            return;
        }

        Entity entity = event.getEntity();
        Material placedMat = null;
        if (entity instanceof ExplosiveMinecart) {
            placedMat = Material.TNT_MINECART;
        } else if (entity instanceof org.bukkit.entity.minecart.RideableMinecart) {
            placedMat = Material.MINECART;
        } else if (entity instanceof org.bukkit.entity.EnderCrystal) {
            placedMat = Material.END_CRYSTAL;
        }

        if (placedMat != null) {
            EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
            final int heldSlot = (hand == EquipmentSlot.HAND) ? player.getInventory().getHeldItemSlot() : -1;
            final Material finalMat = placedMat;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                ItemStack current = (heldSlot >= 0)
                        ? player.getInventory().getItem(heldSlot)
                        : player.getInventory().getItem(hand);
                int targetAmount = (finalMat.getMaxStackSize() > 1 && trainingManager.isOptionEffective(player, TrainingOption.AUTO_REFILL))
                        ? finalMat.getMaxStackSize()
                        : 1;

                if (current == null || current.getType() == Material.AIR || current.getType() == finalMat) {
                    ItemStack restored = new ItemStack(finalMat, targetAmount);
                    if (heldSlot >= 0) player.getInventory().setItem(heldSlot, restored);
                    else player.getInventory().setItem(hand, restored);
                }
                autoRefillHotbar(player);
                player.updateInventory();
            });
        }
    }

    /**
     * Water/Lava/Powder snow bucket: bucket stays full when placed.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (!trainingManager.isOptionEffective(player, TrainingOption.INFINITE_BLOCKS)
                && !trainingManager.isOptionEffective(player, TrainingOption.AUTO_REFILL)) {
            return;
        }

        ItemStack bucketItem = player.getInventory().getItem(event.getHand()).clone();
        event.setItemStack(bucketItem);

        EquipmentSlot hand = event.getHand();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.getInventory().setItem(hand, bucketItem);
            autoRefillHotbar(player);
            player.updateInventory();
        });
    }

    /**
     * Totem of Undying: infinite totem pops without losing the totem.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTotemPop(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!trainingManager.isOptionEffective(player, TrainingOption.INFINITE_TOTEM)
                && !trainingManager.isOptionEffective(player, TrainingOption.AUTO_REFILL)) {
            return;
        }

        EquipmentSlot hand = event.getHand();
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        final boolean inMain = (main.getType() == Material.TOTEM_OF_UNDYING);
        final boolean inOff = (off.getType() == Material.TOTEM_OF_UNDYING);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING, 1);
            if (hand == EquipmentSlot.HAND || inMain) {
                player.getInventory().setItemInMainHand(totem);
            } else if (hand == EquipmentSlot.OFF_HAND || inOff) {
                player.getInventory().setItemInOffHand(totem);
            } else {
                player.getInventory().setItemInOffHand(totem);
            }
            autoRefillHotbar(player);
            player.updateInventory();
        });
    }

    /**
     * Durability protection: tools, armor, shields never lose durability during training.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        if (trainingManager.isOptionEffective(player, TrainingOption.INFINITE_DURABILITY)) {
            event.setCancelled(true);
        }
    }

    /**
     * Invulnerability (God Mode):
     * Completely cancels all incoming damage (falls, pearls, explosions, fire, void, attacks)
     * while God Mode is active in training mode.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (trainingManager.isOptionEffective(player, TrainingOption.GOD_MODE)) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Location respawn = player.getRespawnLocation();
                if (respawn == null) respawn = player.getWorld().getSpawnLocation();
                player.teleport(respawn);
            }
            player.setFireTicks(0);
            player.setFreezeTicks(0);
        }
    }

    /**
     * Infinite Saturation: hunger never depletes during God Mode in training.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (trainingManager.isOptionEffective(player, TrainingOption.GOD_MODE)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
    }

    /**
     * Keep inventory on death in training mode.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (trainingManager.isOptionEffective(player, TrainingOption.KEEP_INVENTORY)) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
    }

    /**
     * Prevents pearl teleports from falsely triggering or maintaining combat tag in Training Mode.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        Player player = event.getPlayer();
        if (trainingManager.isMasterEnabled(player.getUniqueId())) {
            if (FoCombatTagHook.isTagged(player)) {
                FoCombatTagHook.untag(player);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && FoCombatTagHook.isTagged(player)) {
                    FoCombatTagHook.untag(player);
                }
            });
        }
    }

    /**
     * Automatic combat detection:
     * When PvP occurs between two distinct players, immediately auto-disable Training Mode for both!
     * Self-damage (pearls, rocket fireworks, own explosions) NEVER disables training mode!
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        // Ender Pearls never trigger combat detection
        if (event.getDamager() instanceof EnderPearl) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        // PvP combat detected between two different players: auto-disable training mode
        trainingManager.disableTrainingOnCombat(attacker);
        trainingManager.disableTrainingOnCombat(victim);
    }

    /**
     * Auto-refill logic:
     * Refuels stackable items on hotbar up to full stack (64),
     * keeps stackable items in inventory topped up to 64,
     * and pulls items from inventory if hotbar slot was depleted without destroying inventory items.
     */
    public void autoRefillHotbar(Player player) {
        if (!trainingManager.isOptionEffective(player, TrainingOption.AUTO_REFILL)) {
            return;
        }

        // 1. Refill stackable items already on hotbar to full stack (64)
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                Material mat = item.getType();
                if (isRefillableItem(mat) && mat.getMaxStackSize() > 1 && item.getAmount() < mat.getMaxStackSize()) {
                    item.setAmount(mat.getMaxStackSize());
                    player.getInventory().setItem(i, item);
                }
            }
        }

        // 2. Refill stackable items in main inventory (9..35) to full stack (64)
        for (int invSlot = 9; invSlot < 36; invSlot++) {
            ItemStack invItem = player.getInventory().getItem(invSlot);
            if (invItem != null && invItem.getType() != Material.AIR) {
                Material mat = invItem.getType();
                if (isRefillableItem(mat) && mat.getMaxStackSize() > 1 && invItem.getAmount() < mat.getMaxStackSize()) {
                    invItem.setAmount(mat.getMaxStackSize());
                    player.getInventory().setItem(invSlot, invItem);
                }
            }
        }

        // 3. If a hotbar slot is empty, search inventory (9..35) to refill matching training items
        Set<Integer> usedInvSlots = new HashSet<>();
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                for (int invSlot = 9; invSlot < 36; invSlot++) {
                    if (usedInvSlots.contains(invSlot)) continue;
                    ItemStack invItem = player.getInventory().getItem(invSlot);
                    if (invItem != null && invItem.getType() != Material.AIR && isRefillableItem(invItem.getType())) {
                        usedInvSlots.add(invSlot);
                        ItemStack copy = invItem.clone();
                        if (copy.getMaxStackSize() > 1) {
                            copy.setAmount(copy.getMaxStackSize());
                        }
                        player.getInventory().setItem(i, copy);
                        break;
                    }
                }
            }
        }
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) {
            return p;
        }
        return null;
    }

    private boolean isPreservedUsableItem(Material mat) {
        return mat == Material.TNT_MINECART
                || mat == Material.MINECART
                || mat == Material.CHEST_MINECART
                || mat == Material.HOPPER_MINECART
                || mat == Material.FURNACE_MINECART
                || mat == Material.ENDER_PEARL
                || mat == Material.SPLASH_POTION
                || mat == Material.LINGERING_POTION
                || mat == Material.WIND_CHARGE
                || mat == Material.SNOWBALL
                || mat == Material.EGG
                || mat == Material.EXPERIENCE_BOTTLE
                || mat == Material.END_CRYSTAL
                || mat == Material.FIREWORK_ROCKET
                || mat == Material.FIRE_CHARGE
                || mat == Material.GLOWSTONE;
    }

    private boolean isRefillableItem(Material mat) {
        return mat == Material.RAIL
                || mat == Material.POWERED_RAIL
                || mat == Material.ACTIVATOR_RAIL
                || mat == Material.DETECTOR_RAIL
                || mat == Material.TNT_MINECART
                || mat == Material.MINECART
                || mat == Material.OBSIDIAN
                || mat == Material.RESPAWN_ANCHOR
                || mat == Material.GLOWSTONE
                || mat == Material.END_CRYSTAL
                || mat == Material.ENDER_PEARL
                || mat == Material.WIND_CHARGE
                || mat == Material.FIREWORK_ROCKET
                || mat == Material.GOLDEN_APPLE
                || mat == Material.ENCHANTED_GOLDEN_APPLE
                || mat == Material.ARROW
                || mat == Material.TIPPED_ARROW
                || mat == Material.SPECTRAL_ARROW
                || mat == Material.COBWEB
                || mat == Material.TOTEM_OF_UNDYING;
    }
}
