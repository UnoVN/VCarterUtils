package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TotemManager {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    // Luu tru so lan da kich hoat totem trong luot kit hien tai: Map<PlayerUUID, ActivationsCount>
    private final Map<UUID, Integer> totemActivations = new ConcurrentHashMap<>();
    // Cooldown thong bao chong spam chat khi nhat do lien tuc
    private final Map<UUID, Long> lastInventoryLimitMsgTimes = new ConcurrentHashMap<>();

    private final Set<String> cachedBlacklistedWorlds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public TotemManager(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        reload();
    }

    public void reload() {
        cachedBlacklistedWorlds.clear();
        List<String> list = plugin.getConfig().getStringList("totem.blacklisted-worlds");
        for (String w : list) {
            if (w != null && !w.trim().isEmpty()) {
                cachedBlacklistedWorlds.add(w.trim().toLowerCase());
            }
        }
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("totem.enabled", true);
    }

    public boolean isWorldBlacklisted(World world) {
        if (world == null) return false;
        return cachedBlacklistedWorlds.contains(world.getName().trim().toLowerCase());
    }

    public int getMaxInInventory() {
        return Math.max(1, plugin.getConfig().getInt("totem.max-in-inventory", 1));
    }

    public int getMaxActivations() {
        return Math.max(1, plugin.getConfig().getInt("totem.max-activations-per-kit", 1));
    }

    public int getActivations(UUID uuid) {
        return totemActivations.getOrDefault(uuid, 0);
    }

    public void recordActivation(UUID uuid) {
        totemActivations.merge(uuid, 1, Integer::sum);
    }

    public void resetActivations(UUID uuid) {
        totemActivations.remove(uuid);
    }

    /**
     * Counts the total number of totems the player is carrying (including inventory and offhand).
     */
    public int countTotems(Player player) {
        int count = 0;
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Enforces the maximum totem inventory limit for a player.
     */
    public void enforceInventoryLimit(Player player) {
        if (!isEnabled() || isWorldBlacklisted(player.getWorld())) {
            return;
        }

        // Training mode hook: If training mode is active, completely disable anti-totem enforcement
        if (plugin.getTrainingManager() != null && plugin.getTrainingManager().isPlayerEnabled(player.getUniqueId())) {
            return;
        }

        int max = getMaxInInventory();
        int total = countTotems(player);
        if (total <= max) {
            return;
        }

        int excess = total - max;
        PlayerInventory inv = player.getInventory();
        String action = plugin.getConfig().getString("totem.excess-action", "REMOVE").toUpperCase();
        boolean removedAny = false;

        // Priority: keep Offhand (40) and Hotbar (0-8), remove excess from main inventory storage (9-35) first
        int[] removalOrder = getRemovalSlotOrder();

        for (int slot : removalOrder) {
            if (excess <= 0) break;

            ItemStack item = (slot == 40) ? inv.getItemInOffHand() : inv.getItem(slot);
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                int amount = item.getAmount();
                if (amount <= excess) {
                    excess -= amount;
                    if (slot == 40) inv.setItemInOffHand(null);
                    else inv.setItem(slot, null);

                    handleExcessItem(player, item, action);
                    removedAny = true;
                } else {
                    item.setAmount(amount - excess);
                    ItemStack removedStack = item.clone();
                    removedStack.setAmount(excess);
                    excess = 0;

                    handleExcessItem(player, removedStack, action);
                    removedAny = true;
                    break;
                }
            }
        }

        if (removedAny) {
            sendInventoryLimitMessage(player);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private void handleExcessItem(Player player, ItemStack item, String action) {
        if ("DROP".equals(action)) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        // If action is REMOVE, nothing further needed as item is already removed
    }

    private int[] getRemovalSlotOrder() {
        int[] order = new int[37];
        int idx = 0;
        // Clear storage slots first: 35 down to 9
        for (int i = 35; i >= 9; i--) {
            order[idx++] = i;
        }
        // Then clear hotbar slots: 8 down to 0
        for (int i = 8; i >= 0; i--) {
            order[idx++] = i;
        }
        // Offhand slot (40) is lowest removal priority
        order[idx] = 40;
        return order;
    }

    public void sendInventoryLimitMessage(Player player) {
        long now = System.currentTimeMillis();
        Long lastTime = lastInventoryLimitMsgTimes.get(player.getUniqueId());
        // Cooldown 2.5 giay chong spam tin nhan chat
        if (lastTime != null && (now - lastTime < 2500L)) {
            return;
        }
        lastInventoryLimitMsgTimes.put(player.getUniqueId(), now);

        String raw = plugin.getConfig().getString(
                "totem.messages.inventory-limit-reached",
                "%prefix_error%%c_error%<b>ᴛᴏᴛᴇᴍ ʟɪᴍɪᴛ</b> %c_muted%» %c_text%You can only carry %c_primary%<b>%max%</b> %c_text%Totem of Undying."
        );
        player.sendMessage(plugin.parseMessage(raw.replace("%max%", String.valueOf(getMaxInInventory()))));
    }

    public void sendActivationLimitMessage(Player player) {
        String raw = plugin.getConfig().getString(
                "totem.messages.activation-limit-reached",
                "%prefix_error%%c_error%<b>ᴛᴏᴛᴇᴍ ᴅᴇɴɪᴇᴅ</b> %c_muted%» %c_text%You have already used your Totem for this kit."
        );
        player.sendMessage(plugin.parseMessage(raw));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    public void clearAll() {
        totemActivations.clear();
        lastInventoryLimitMsgTimes.clear();
        cachedBlacklistedWorlds.clear();
    }
  }
