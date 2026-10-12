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
import org.bukkit.inventory.meta.CrossbowMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RegearManager {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    // Cache danh sach whitelist da duoc loc bo hoan toan cac item bi cam
    private final Set<Material> cachedWhitelist = EnumSet.noneOf(Material.class);

    // Snapshot layout kit goc cua tung nguoi choi
    private final Map<UUID, ItemStack[]> savedKitSnapshots = new ConcurrentHashMap<>();
    // Combat Tag
    private final Map<UUID, Long> lastDamageTimes = new ConcurrentHashMap<>();
    // Regear Cooldown
    private final Map<UUID, Long> lastRegearTimes = new ConcurrentHashMap<>();

    public RegearManager(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        reload();
    }

    /**
     * Kiem tra vat pham bi CAM HOAN TOAN khoi tinh nang Regear:
     * - Firework (phao hoa)
     * - Bow (cung)
     * - Swords (tat ca cac loai kiem)
     * - Experience Bottle (chai exp)
     */
    public boolean isForbidden(Material mat) {
        if (mat == null) {
            return true;
        }

        // Cam tat ca cac loai Kiem
        if (mat.name().endsWith("_SWORD")) {
            return true;
        }

        // Cam Phao hoa
        if (mat == Material.FIREWORK_ROCKET || mat == Material.FIREWORK_STAR) {
            return true;
        }

        // Cam Cung
        if (mat == Material.BOW) {
            return true;
        }

        // Cam Chai EXP
        if (mat == Material.EXPERIENCE_BOTTLE) {
            return true;
        }

        return false;
    }

    /**
     * Load va parse danh sach whitelist tu config
     */
    public void reload() {
        cachedWhitelist.clear();
        List<String> list = plugin.getConfig().getStringList("regear.whitelist");

        for (String entry : list) {
            String clean = entry.trim().toUpperCase();
            if (clean.isEmpty()) continue;

            // 1. Nhom vu khi va dung cu (DA LOAI BO SWORDS VA BOWS)
            if (clean.equals("AXES") || clean.equals("AXE")) {
                for (Material m : Material.values()) {
                    if (m.name().endsWith("_AXE") && !isForbidden(m)) {
                        cachedWhitelist.add(m);
                    }
                }
            } else if (clean.equals("PICKAXES") || clean.equals("PICKAXE")) {
                for (Material m : Material.values()) {
                    if (m.name().endsWith("_PICKAXE") && !isForbidden(m)) {
                        cachedWhitelist.add(m);
                    }
                }
            } else if (clean.equals("ARROWS") || clean.equals("ARROW")) {
                cachedWhitelist.add(Material.ARROW);
                cachedWhitelist.add(Material.SPECTRAL_ARROW);
                cachedWhitelist.add(Material.TIPPED_ARROW);
            } 
            // 2. Nhom khoi xay dung va duong ray
            else if (clean.equals("PLANKS") || clean.contains("PLANK")) {
                for (Material m : Material.values()) {
                    if (m.name().endsWith("_PLANKS") && !isForbidden(m)) {
                        cachedWhitelist.add(m);
                    }
                }
            } else if (clean.equals("RAILS") || clean.equals("RAIL")) {
                for (Material m : Material.values()) {
                    if ((m.name().endsWith("_RAIL") || m == Material.RAIL) && !isForbidden(m)) {
                        cachedWhitelist.add(m);
                    }
                }
            } else if (clean.equals("WOOD") || clean.equals("LOGS") || clean.equals("LOG") || clean.equals("WOODS")) {
                for (Material m : Material.values()) {
                    if ((m.name().endsWith("_LOG") || m.name().endsWith("_WOOD") ||
                         m.name().endsWith("_STEM") || m.name().startsWith("STRIPPED_") ||
                         m == Material.BAMBOO_BLOCK) && !isForbidden(m)) {
                        cachedWhitelist.add(m);
                    }
                }
            } 
            // 3. Nhom thuoc va vat pham dac thu
            else if (clean.equals("POTIONS") || clean.equals("SPLASH_POTIONS") || clean.equals("SPLASH_POTION")) {
                cachedWhitelist.add(Material.SPLASH_POTION);
                cachedWhitelist.add(Material.POTION);
                cachedWhitelist.add(Material.LINGERING_POTION);
            } else if (clean.equals("WEB")) {
                cachedWhitelist.add(Material.COBWEB);
            } else {
                // Parse truc tiep ten Material va chan neu thuoc danh sach cam
                Material mat = Material.matchMaterial(clean);
                if (mat != null && !isForbidden(mat)) {
                    cachedWhitelist.add(mat);
                }
            }
        }

        // Dam bao loai bo 100% cac vat pham bi cam khoi cache
        cachedWhitelist.removeIf(this::isForbidden);
    }

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

        // 4. DUYET TUNG SLOT VA GHI DE DUNG VI TRI
        for (int slot = 0; slot < snapshot.length; slot++) {
            ItemStack originalItem = snapshot[slot];

            // Chi bo qua giap (36-39) neu vat pham khong phai Elytra / khong nam trong whitelist
            if (slot >= 36 && slot <= 39) {
                if (originalItem == null || !isWhitelisted(originalItem.getType())) {
                    continue;
                }
            }

            // TH1: Slot goc trong kit la vat pham thuoc Whitelist va KHONG BI CAM
            if (originalItem != null && isWhitelisted(originalItem.getType())) {
                ItemStack currentItem = inv.getItem(slot);
                boolean needRestore = false;

                // Neu slot dang trong, hoac bi rac / block la chen vao
                if (currentItem == null || currentItem.getType() != originalItem.getType()) {
                    needRestore = true;
                }
                // Neu stack vat pham bi thieu so luong (Ender Pearl, Pot, Blocks, v.v.)
                else if (currentItem.getAmount() < originalItem.getAmount()) {
                    needRestore = true;
                }
                // Xu ly rieng cho NO (CROSSBOW): Kiem tra tinh trang dan da nap
                else if (originalItem.getType() == Material.CROSSBOW) {
                    if (originalItem.getItemMeta() instanceof CrossbowMeta origMeta &&
                        currentItem.getItemMeta() instanceof CrossbowMeta curMeta) {
                        if (!origMeta.getChargedProjectiles().equals(curMeta.getChargedProjectiles())) {
                            needRestore = true;
                        }
                    } else if (!currentItem.isSimilar(originalItem)) {
                        needRestore = true;
                    }
                }
                // Cac vat pham khac neu khong con nguyen ban
                else if (!currentItem.isSimilar(originalItem)) {
                    needRestore = true;
                }

                if (needRestore) {
                    ItemStack restored = originalItem.clone();
                    if (slot == 40) {
                        inv.setItemInOffHand(restored);
                    } else if (slot == 38) {
                        inv.setChestplate(restored);
                    } else {
                        inv.setItem(slot, restored);
                    }
                }
            }
            // TH2: Slot goc trong kit von di la O TRONG -> Xoa sach rac nhat ngoai san
            else if (originalItem == null && clearJunkInEmpty) {
                ItemStack currentItem = inv.getItem(slot);
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

        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
        sendConfigMessage(player, "regear.messages.success", "%seconds%", "0");

        // Broadcast thong bao regear
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

    public boolean isWhitelisted(Material mat) {
        return mat != null && !isForbidden(mat) && cachedWhitelist.contains(mat);
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
        cachedWhitelist.clear();
    }
}
