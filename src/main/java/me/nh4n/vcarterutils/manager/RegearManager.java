package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RegearManager {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    // Snapshot layout kit goc cua tung nguoi choi
    private final Map<UUID, ItemStack[]> savedKitSnapshots = new ConcurrentHashMap<>();
    // Combat Tag (thoi diem nhan sat thuong gan nhat)
    private final Map<UUID, Long> lastDamageTimes = new ConcurrentHashMap<>();
    // Regear Cooldown
    private final Map<UUID, Long> lastRegearTimes = new ConcurrentHashMap<>();

    public RegearManager(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Luu ban snapshot day du 41 slot ngay khi player load kit
     */
    public void saveKitSnapshot(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] currentContents = inv.getContents();
        ItemStack[] snapshot = new ItemStack[currentContents.length];

        for (int i = 0; i < currentContents.length; i++) {
            if (currentContents[i] != null && currentContents[i].getType() != Material.AIR) {
                snapshot[i] = currentContents[i].clone();
            } else {
                snapshot[i] = null;
            }
        }

        savedKitSnapshots.put(player.getUniqueId(), snapshot);
    }

    public void recordDamage(UUID playerId) {
        lastDamageTimes.put(playerId, System.currentTimeMillis());
    }

    /**
     * Thuc thi Regear tu than 100%: De thang len rac/item la va refill dung slot whitelist
     */
    public boolean performRegear(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // 1. Kiem tra Combat Tag
        long damageCooldownSec = plugin.getConfig().getLong("regear.damage-cooldown-seconds", 5L);
        if (damageCooldownSec > 0) {
            Long lastDmg = lastDamageTimes.get(uuid);
            if (lastDmg != null) {
                long elapsed = (now - lastDmg) / 1000L;
                if (elapsed < damageCooldownSec) {
                    long remaining = damageCooldownSec - elapsed;
                    sendConfigMessage(player, "regear.messages.combat-tagged", "%seconds%", String.valueOf(remaining));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return false;
                }
            }
        }

        // 2. Kiem tra Cooldown giua 2 lan regear
        long regearCooldownSec = plugin.getConfig().getLong("regear.cooldown-seconds", 3L);
        if (regearCooldownSec > 0) {
            Long lastRg = lastRegearTimes.get(uuid);
            if (lastRg != null) {
                long elapsed = (now - lastRg) / 1000L;
                if (elapsed < regearCooldownSec) {
                    long remaining = regearCooldownSec - elapsed;
                    sendConfigMessage(player, "regear.messages.on-cooldown", "%seconds%", String.valueOf(remaining));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return false;
                }
            }
        }

        // 3. Kiem tra Snapshot kit
        ItemStack[] snapshot = savedKitSnapshots.get(uuid);
        if (snapshot == null) {
            sendConfigMessage(player, "regear.messages.no-kit-loaded", "%seconds%", "0");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        PlayerInventory inv = player.getInventory();
        boolean clearJunkInEmpty = plugin.getConfig().getBoolean("regear.clear-junk-in-empty-slots", true);

        // 4. DUYET TUNG SLOT VA GHI DE DUNG VI TRI (0-35: Storage/Hotbar, 40: Offhand)
        for (int slot = 0; slot < snapshot.length; slot++) {
            // Bo qua cac slot giap (36, 37, 38, 39) de giu nguyen do ben thuc chien
            if (slot >= 36 && slot <= 39) {
                continue;
            }

            ItemStack originalItem = snapshot[slot];

            // TH1: Slot goc trong kit la item thuoc Whitelist -> BAT BUOC GHI DE VA PHUC HOI
            if (originalItem != null && isWhitelisted(originalItem.getType())) {
                ItemStack currentItem = inv.getItem(slot);

                // Bat ke o do dang trong, dang bi rac/block la chiem, hay bi hao hut so luong -> DE THANG
                if (currentItem == null || currentItem.getType() != originalItem.getType() || currentItem.getAmount() < originalItem.getAmount()) {
                    if (slot == 40) {
                        inv.setItemInOffHand(originalItem.clone());
                    } else {
                        inv.setItem(slot, originalItem.clone());
                    }
                }
            }
            // TH2: Slot goc trong kit von di la O TRONG (null)
            else if (originalItem == null && clearJunkInEmpty) {
                ItemStack currentItem = inv.getItem(slot);
                // Neu player nhat phai rac/block linh tinh khong co trong kit goc -> XOA SACH
                if (currentItem != null && !isItemInOriginalKit(currentItem.getType(), snapshot)) {
                    if (slot == 40) {
                        inv.setItemInOffHand(null);
                    } else {
                        inv.setItem(slot, null);
                    }
                }
            }
        }

        lastRegearTimes.put(uuid, now);

        // Hieu ung am thanh
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
        sendConfigMessage(player, "regear.messages.success", "%seconds%", "0");

        // 5. BROADCAST ANNOUNCEMENT LEN CHAT CHO REGEAR (Catppuccin Palette)
        broadcastRegear(player);

        return true;
    }

    private void broadcastRegear(Player player) {
        if (!plugin.getConfig().getBoolean("regear.announcement.enabled", true)) {
            return;
        }

        String raw = plugin.getConfig().getString(
                "regear.announcement.message",
                "<glyph:warning_blue> <#89dceb><b>%player%</b> <#a6adc8>regeared"
        );
        Component comp = miniMessage.deserialize(raw.replace("%player%", player.getName()));

        if (plugin.getConfig().getBoolean("regear.announcement.broadcast", true)) {
            Bukkit.broadcast(comp);
        } else {
            player.sendMessage(comp);
        }
    }

    /**
     * Kiem tra Whitelist ho tro Smart Category Matcher
     */
    public boolean isWhitelisted(Material mat) {
        if (mat == null) return false;
        String name = mat.name();

        // Danh muc dac thu
        if (name.equals("FIREWORK_ROCKET") || name.equals("ENDER_PEARL") ||
            name.equals("TNT_MINECART") || name.equals("SPLASH_POTION") ||
            name.equals("CROSSBOW") || name.equals("COBWEB")) {
            return true;
        }

        // Tat ca cac loai Rail
        if (name.endsWith("_RAIL") || name.equals("RAIL")) {
            return true;
        }

        // Tat ca cac loai Planks, Logs, Wood, Stems
        if (name.endsWith("_PLANKS") || name.endsWith("_LOG") ||
            name.endsWith("_WOOD") || name.endsWith("_STEM") ||
            name.startsWith("STRIPPED_") || name.equals("BAMBOO_BLOCK")) {
            return true;
        }

        // Kiem tra them trong config list neu co bo sung them
        List<String> list = plugin.getConfig().getStringList("regear.whitelist");
        for (String entry : list) {
            Material m = Material.matchMaterial(entry.trim().toUpperCase());
            if (m != null && m == mat) {
                return true;
            }
        }

        return false;
    }

    private boolean isItemInOriginalKit(Material mat, ItemStack[] snapshot) {
        for (ItemStack item : snapshot) {
            if (item != null && item.getType() == mat) {
                return true;
            }
        }
        return false;
    }

    private void sendConfigMessage(Player player, String path, String placeholder, String value) {
        String raw = plugin.getConfig().getString(path);
        if (raw != null && !raw.isEmpty()) {
            player.sendMessage(miniMessage.deserialize(raw.replace(placeholder, value)));
        }
    }

    public void clearAll() {
        savedKitSnapshots.clear();
        lastDamageTimes.clear();
        lastRegearTimes.clear();
    }
}
