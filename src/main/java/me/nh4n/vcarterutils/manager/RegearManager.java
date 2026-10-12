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

    // Cache danh sach whitelist da duoc loc bo hoan toan cac item bi cam
    private final Set<Material> cachedWhitelist = EnumSet.noneOf(Material.class);

    // Snapshot layout kit goc cua tung nguoi choi (luu day du 41 slot khi vua load kit)
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
     * Danh sach vat pham TUYET DOI KHONG TU DONG CAP MOI / REFILL SO LUONG:
     * - Tat ca cac loai Kiem (giu nguyen do ben thuc chien)
     * - Cung (Bow)
     * - Phao hoa (Firework - giu nguyen so luong con lai, khong hoi them)
     * - Chai EXP (giu nguyen so chai con lai, khong hoi them)
     */
    public boolean isForbiddenFromRefill(Material mat) {
        if (mat == null) return true;
        if (mat.name().endsWith("_SWORD")) return true;
        if (mat == Material.BOW) return true;
        if (mat == Material.FIREWORK_ROCKET || mat == Material.FIREWORK_STAR) return true;
        if (mat == Material.EXPERIENCE_BOTTLE) return true;
        return false;
    }

    public void reload() {
        cachedWhitelist.clear();
        List<String> list = plugin.getConfig().getStringList("regear.whitelist");

        for (String entry : list) {
            String clean = entry.trim().toUpperCase();
            if (clean.isEmpty()) continue;

            if (clean.equals("AXES") || clean.equals("AXE")) {
                for (Material m : Material.values()) {
                    if (m.name().endsWith("_AXE") && !isForbiddenFromRefill(m)) {
                        cachedWhitelist.add(m);
                    }
                }
            } else if (clean.equals("PICKAXES") || clean.equals("PICKAXE")) {
                for (Material m : Material.values()) {
                    if (m.name().endsWith("_PICKAXE") && !isForbiddenFromRefill(m)) {
                        cachedWhitelist.add(m);
                    }
                }
            } else if (clean.equals("ARROWS") || clean.equals("ARROW")) {
                cachedWhitelist.add(Material.ARROW);
                cachedWhitelist.add(Material.SPECTRAL_ARROW);
                cachedWhitelist.add(Material.TIPPED_ARROW);
            } else if (clean.equals("PLANKS") || clean.contains("PLANK")) {
                for (Material m : Material.values()) {
                    if (m.name().endsWith("_PLANKS") && !isForbiddenFromRefill(m)) {
                        cachedWhitelist.add(m);
                    }
                }
            } else if (clean.equals("RAILS") || clean.equals("RAIL")) {
                for (Material m : Material.values()) {
                    if ((m.name().endsWith("_RAIL") || m == Material.RAIL) && !isForbiddenFromRefill(m)) {
                        cachedWhitelist.add(m);
                    }
                }
            } else if (clean.equals("WOOD") || clean.equals("LOGS") || clean.equals("LOG") || clean.equals("WOODS")) {
                for (Material m : Material.values()) {
                    if ((m.name().endsWith("_LOG") || m.name().endsWith("_WOOD") ||
                         m.name().endsWith("_STEM") || m.name().startsWith("STRIPPED_") ||
                         m == Material.BAMBOO_BLOCK) && !isForbiddenFromRefill(m)) {
                        cachedWhitelist.add(m);
                    }
                }
            } else if (clean.equals("POTIONS") || clean.equals("SPLASH_POTIONS") || clean.equals("SPLASH_POTION")) {
                cachedWhitelist.add(Material.SPLASH_POTION);
                cachedWhitelist.add(Material.POTION);
                cachedWhitelist.add(Material.LINGERING_POTION);
            } else if (clean.equals("WEB")) {
                cachedWhitelist.add(Material.COBWEB);
            } else {
                Material mat = Material.matchMaterial(clean);
                if (mat != null && !isForbiddenFromRefill(mat)) {
                    cachedWhitelist.add(mat);
                }
            }
        }

        cachedWhitelist.removeIf(this::isForbiddenFromRefill);
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

    /**
     * Thuat toan Regear chuyen sau:
     * 1. Gom toan bo vat pham thuc te dang co (Phao hoa con lai, EXP con lai, Kiem da mon do ben).
     * 2. Uu tien phuc hoi 100% Hotbar (Slot 0-8) ve y chang vi tri luc vua load kit ban dau.
     * 3. Refill dung cac item Whitelist ma khong bao gio lam mat Phao hoa, EXP hay vat pham hop le.
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
        ItemStack[] currentContents = inv.getContents();

        // Thu thap toan bo vat pham thuc te vao pool de bao toan so luong va do ben
        List<ItemStack> currentPool = new ArrayList<>();
        for (ItemStack item : currentContents) {
            if (item != null && item.getType() != Material.AIR) {
                currentPool.add(item.clone());
            }
        }

        ItemStack[] newContents = new ItemStack[currentContents.length];

        // --- BUOC 1: Xu ly Giap (Slots 36 - 39) ---
        for (int a = 36; a <= 39; a++) {
            ItemStack snapArmor = (a < snapshot.length) ? snapshot[a] : null;
            if (a == 38 && snapArmor != null && isWhitelisted(snapArmor.getType())) {
                newContents[a] = snapArmor.clone();
            } else {
                ItemStack currentArmor = currentContents[a];
                newContents[a] = (currentArmor != null && currentArmor.getType() != Material.AIR) ? currentArmor.clone() : null;
            }
            if (currentContents[a] != null) {
                removeFirstMatch(currentPool, currentContents[a]);
            }
        }

        // --- BUOC 2: Uu tien sap xep Hotbar (0 - 8) va Offhand (40) ve y chang kit ban dau ---
        int[] priorityOrder = getSlotPriorityOrder(); // 0..8, 40, 9..35

        // Phase 2.1: Phuc hoi cac vat pham khong refill (Phao hoa, EXP con lai, Kiem, Cung) ve dung slot Hotbar
        for (int slot : priorityOrder) {
            if (slot >= snapshot.length) continue;
            ItemStack snapItem = snapshot[slot];
            if (snapItem == null || snapItem.getType() == Material.AIR) continue;

            if (isForbiddenFromRefill(snapItem.getType()) || !isWhitelisted(snapItem.getType())) {
                ItemStack matched = extractMatchingAmount(currentPool, snapItem);
                if (matched != null) {
                    newContents[slot] = matched; // Tra vat pham thuc te ve dung slot goc!
                } else {
                    newContents[slot] = null;
                }
            }
        }

        // Phase 2.2: Refill cac vat pham thuoc Whitelist ve dung slot Hotbar va Inventory
        for (int slot : priorityOrder) {
            if (slot >= snapshot.length) continue;
            ItemStack snapItem = snapshot[slot];
            if (snapItem == null || snapItem.getType() == Material.AIR) continue;

            if (isWhitelisted(snapItem.getType()) && !isForbiddenFromRefill(snapItem.getType())) {
                newContents[slot] = snapItem.clone();
                // Loai bo item tuong ung khoi currentPool de tranh bi nhan ban
                removeFirstMatch(currentPool, snapItem);
            }
        }

        // --- BUOC 3: Xu ly cac vat pham hop le con sot lai trong Pool (Chong mat Phao hoa & EXP) ---
        for (ItemStack leftover : currentPool) {
            if (leftover == null || leftover.getType() == Material.AIR) continue;

            if (isItemInOriginalKit(leftover.getType(), snapshot)) {
                // Thu gom gop vao cac stack da co trong kho do truoc
                for (int s = 0; s <= 35; s++) {
                    ItemStack existing = newContents[s];
                    if (existing != null && existing.isSimilar(leftover) && existing.getAmount() < existing.getMaxStackSize()) {
                        int canAdd = existing.getMaxStackSize() - existing.getAmount();
                        int toAdd = Math.min(canAdd, leftover.getAmount());
                        existing.setAmount(existing.getAmount() + toAdd);
                        leftover.setAmount(leftover.getAmount() - toAdd);
                        if (leftover.getAmount() <= 0) break;
                    }
                }
                // Neu van con du thi dat vao o trong cua inventory (9-35)
                if (leftover.getAmount() > 0) {
                    int emptySlot = findFirstEmptyStorageSlot(newContents);
                    if (emptySlot != -1) {
                        newContents[emptySlot] = leftover;
                    }
                }
            }
            // Cac khoi Dirt, Cobblestone, rac ngoai san khong co trong snapshot -> Tu dong bien mat
        }

        // Cap nhat toan bo kho do
        inv.setContents(newContents);
        if (newContents.length > 40 && newContents[40] != null) {
            inv.setItemInOffHand(newContents[40]);
        }

        lastRegearTimes.put(uuid, now);

        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
        sendConfigMessage(player, "regear.messages.success", "%seconds%", "0");

        broadcastRegear(player);
        return true;
    }

    /**
     * Thu tu uu tien: Hotbar (0-8) xep truoc, sau do den Tay phu (40), roi moi den Rương (9-35)
     */
    private int[] getSlotPriorityOrder() {
        int[] order = new int[37];
        int idx = 0;
        for (int i = 0; i <= 8; i++) {
            order[idx++] = i;
        }
        order[idx++] = 40;
        for (int i = 9; i <= 35; i++) {
            order[idx++] = i;
        }
        return order;
    }

    /**
     * Trich xuat va tu dong gop cac coc vat pham (Phao hoa / EXP / Mui ten) bi tach le
     */
    private ItemStack extractMatchingAmount(List<ItemStack> pool, ItemStack target) {
        if (target == null || target.getType() == Material.AIR) return null;
        ItemStack result = null;

        for (int i = pool.size() - 1; i >= 0; i--) {
            ItemStack item = pool.get(i);
            if (item != null && item.getType() == target.getType()) {
                if (result == null) {
                    result = pool.remove(i);
                    if (result.getMaxStackSize() == 1 || result.getAmount() >= target.getAmount()) {
                        return result;
                    }
                } else {
                    int canAdd = target.getAmount() - result.getAmount();
                    if (canAdd <= 0) break;
                    if (item.getAmount() <= canAdd) {
                        result.setAmount(result.getAmount() + item.getAmount());
                        pool.remove(i);
                    } else {
                        result.setAmount(result.getAmount() + canAdd);
                        item.setAmount(item.getAmount() - canAdd);
                        break;
                    }
                }
            }
        }
        return result;
    }

    private void removeFirstMatch(List<ItemStack> pool, ItemStack target) {
        if (target == null || target.getType() == Material.AIR) return;
        for (int i = 0; i < pool.size(); i++) {
            ItemStack item = pool.get(i);
            if (item != null && item.getType() == target.getType()) {
                pool.remove(i);
                return;
            }
        }
    }

    private int findFirstEmptyStorageSlot(ItemStack[] contents) {
        for (int i = 9; i <= 35; i++) {
            if (contents[i] == null || contents[i].getType() == Material.AIR) {
                return i;
            }
        }
        return -1;
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
        return mat != null && !isForbiddenFromRefill(mat) && cachedWhitelist.contains(mat);
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
