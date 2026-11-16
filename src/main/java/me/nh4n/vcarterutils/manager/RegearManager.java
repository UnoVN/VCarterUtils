package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.hook.FoCombatTagHook;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RegearManager {

    private final VCarterUtils plugin;
    private final NamespacedKey regearKey;

    private final Set<Material> cachedWhitelist = EnumSet.noneOf(Material.class);
    private final Set<Material> cachedPreservedItems = EnumSet.noneOf(Material.class);
    private final Map<UUID, ItemStack[]> savedKitSnapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDamageTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRegearTimes = new ConcurrentHashMap<>();

    private volatile long damageCooldownSec = 5L;
    private volatile long regearCooldownSec = 3L;
    private volatile boolean checkCombatTag = true;

    public RegearManager(VCarterUtils plugin) {
        this.plugin = plugin;
        this.regearKey = new NamespacedKey(plugin, "vcarter_regear_shulker");
        reload();
    }

    public NamespacedKey getRegearKey() {
        return regearKey;
    }

    public boolean isRegearItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(regearKey, PersistentDataType.BYTE);
    }

    public void removeAllRegearItems(Player player) {
        if (player == null) return;
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isRegearItem(contents[i])) {
                inv.setItem(i, null);
            }
        }
        if (isRegearItem(inv.getItemInOffHand())) {
            inv.setItemInOffHand(null);
        }
        if (isRegearItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    public boolean isPreservedItem(Material mat) {
        return mat != null && cachedPreservedItems.contains(mat);
    }

    public boolean isForbiddenFromRefill(Material mat) {
        if (mat == null) return true;
        if (isPreservedItem(mat)) return true;
        if (mat.name().endsWith("_SWORD")) return true;
        if (mat == Material.BOW) return true;
        if (mat == Material.FIREWORK_ROCKET || mat == Material.FIREWORK_STAR) return true;
        if (mat == Material.EXPERIENCE_BOTTLE) return true;
        return false;
    }

    public void reload() {
        cachedWhitelist.clear();
        cachedPreservedItems.clear();

        FileConfiguration cfg = plugin.getConfig();

        this.damageCooldownSec = cfg.getLong("regear.damage-cooldown-seconds", 5L);
        this.regearCooldownSec = cfg.getLong("regear.cooldown-seconds", 3L);
        this.checkCombatTag = cfg.getBoolean("regear.check-combat-tag", true);

        // 1. Load Preserve Items (not cloned from snapshot, preserves current durability, never deleted)
        List<String> preserveList = cfg.getStringList("regear.preserve-items");
        if (preserveList.isEmpty()) {
            preserveList = List.of("ELYTRA", "FLINT_AND_STEEL", "BOW", "SWORDS");
        }

        for (String entry : preserveList) {
            String clean = entry.trim().toUpperCase();
            if (clean.isEmpty()) continue;

            if (clean.equals("SWORDS") || clean.equals("SWORD")) {
                for (Material m : Material.values()) {
                    if (m.name().endsWith("_SWORD")) {
                        cachedPreservedItems.add(m);
                    }
                }
            } else if (clean.equals("BOW") || clean.equals("BOWS")) {
                cachedPreservedItems.add(Material.BOW);
            } else if (clean.equals("ELYTRA") || clean.equals("ELYTRAS")) {
                cachedPreservedItems.add(Material.ELYTRA);
            } else if (clean.equals("FLINT") || clean.equals("FLINT_AND_STEEL") || clean.equals("FLINT_STEEL")) {
                cachedPreservedItems.add(Material.FLINT_AND_STEEL);
            } else if (clean.equals("CROSSBOW") || clean.equals("CROSSBOWS")) {
                cachedPreservedItems.add(Material.CROSSBOW);
            } else if (clean.equals("MACE") || clean.equals("MACES")) {
                cachedPreservedItems.add(Material.MACE);
            } else if (clean.equals("SHIELD") || clean.equals("SHIELDS")) {
                cachedPreservedItems.add(Material.SHIELD);
            } else if (clean.equals("FISHING_ROD") || clean.equals("FISHING_RODS")) {
                cachedPreservedItems.add(Material.FISHING_ROD);
            } else {
                Material mat = Material.matchMaterial(clean);
                if (mat != null) {
                    cachedPreservedItems.add(mat);
                }
            }
        }

        // 2. Load Whitelist (items fully restocked from snapshot)
        List<String> list = cfg.getStringList("regear.whitelist");

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
        cachedWhitelist.removeIf(this::isPreservedItem);
    }

    public void saveKitSnapshot(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] currentContents = inv.getContents();
        ItemStack[] snapshot = new ItemStack[currentContents.length];
        LobbyHotbarManager lobbyManager = plugin.getLobbyHotbarManager();

        for (int i = 0; i < currentContents.length; i++) {
            ItemStack item = currentContents[i];
            if (item != null && item.getType() != Material.AIR
                    && !isRegearItem(item)
                    && (lobbyManager == null || !lobbyManager.isLobbyItem(item))) {
                snapshot[i] = item.clone();
            } else {
                snapshot[i] = null;
            }
        }

        savedKitSnapshots.put(player.getUniqueId(), snapshot);
    }

    public void recordDamage(UUID playerId) {
        lastDamageTimes.put(playerId, System.currentTimeMillis());
    }

    public boolean canRegear(Player player, boolean sendMessages) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // 1. Combat & Damage Cooldown
        if (damageCooldownSec > 0) {
            Long lastDmg = lastDamageTimes.get(uuid);
            if (lastDmg != null) {
                long elapsed = (now - lastDmg) / 1000L;
                if (elapsed < damageCooldownSec) {
                    if (sendMessages) {
                        long remaining = damageCooldownSec - elapsed;
                        sendConfigMessage(player, "regear.messages.combat-tagged", "%seconds%", String.valueOf(remaining));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                    return false;
                }
            }

            if (checkCombatTag && FoCombatTagHook.isTagged(player)) {
                long remaining = FoCombatTagHook.getRemainingSeconds(player);
                if (remaining <= 0 || (damageCooldownSec > 0 && remaining > damageCooldownSec)) {
                    remaining = damageCooldownSec;
                }
                if (sendMessages) {
                    sendConfigMessage(player, "regear.messages.combat-tagged", "%seconds%", String.valueOf(remaining));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                return false;
            }
        }

        // 2. Cooldown
        if (regearCooldownSec > 0) {
            Long lastRg = lastRegearTimes.get(uuid);
            if (lastRg != null) {
                long elapsed = (now - lastRg) / 1000L;
                if (elapsed < regearCooldownSec) {
                    if (sendMessages) {
                        long remaining = regearCooldownSec - elapsed;
                        sendConfigMessage(player, "regear.messages.on-cooldown", "%seconds%", String.valueOf(remaining));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                    return false;
                }
            }
        }

        // 3. Snapshot check
        ItemStack[] snapshot = savedKitSnapshots.get(uuid);
        if (snapshot == null) {
            if (sendMessages) {
                sendConfigMessage(player, "regear.messages.no-kit-loaded", "%seconds%", "0");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return false;
        }

        return true;
    }

    public boolean performRegear(Player player) {
        if (!canRegear(player, true)) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        ItemStack[] snapshot = savedKitSnapshots.get(uuid);
        if (snapshot == null) {
            return false;
        }

        PlayerInventory inv = player.getInventory();
        ItemStack[] currentContents = inv.getContents();
        LobbyHotbarManager lobbyManager = plugin.getLobbyHotbarManager();

        List<ItemStack> currentPool = new ArrayList<>();
        for (ItemStack item : currentContents) {
            if (item != null && item.getType() != Material.AIR
                    && !isRegearItem(item)
                    && (lobbyManager == null || !lobbyManager.isLobbyItem(item))) {
                currentPool.add(item.clone());
            }
        }

        ItemStack[] newContents = new ItemStack[currentContents.length];

        // --- STEP 1: Armor (Slots 36 - 39) ---
        for (int a = 36; a <= 39; a++) {
            ItemStack snapArmor = (a < snapshot.length) ? snapshot[a] : null;
            if (snapArmor != null && isRegearItem(snapArmor)) {
                snapArmor = null;
            }
            ItemStack currentArmor = currentContents[a];

            if (currentArmor != null && currentArmor.getType() != Material.AIR && !isRegearItem(currentArmor)) {
                // Preserve current equipped armor state (durability, enchantments), including worn Elytra at slot 38
                newContents[a] = currentArmor.clone();
                removeFirstMatch(currentPool, currentArmor);
            } else if (snapArmor != null && snapArmor.getType() != Material.AIR) {
                // Armor slot is empty, check if an unequipped preserved item exists in current inventory
                if (isPreservedItem(snapArmor.getType())) {
                    ItemStack matched = extractMatchingAmount(currentPool, snapArmor);
                    newContents[a] = matched;
                } else if (isWhitelisted(snapArmor.getType())) {
                    newContents[a] = snapArmor.clone();
                }
            }
        }

        // --- STEP 2: Hotbar (0 - 8), Offhand (40) and Storage (9 - 35) ---
        int[] priorityOrder = getSlotPriorityOrder();

        // 2A: Prioritize preserve-items and forbiddenFromRefill (Bow, Flint, Elytra, Sword...)
        // Extracts the player's existing item from currentPool to preserve state and durability
        for (int slot : priorityOrder) {
            if (slot >= snapshot.length) continue;
            ItemStack snapItem = snapshot[slot];
            if (snapItem == null || snapItem.getType() == Material.AIR || isRegearItem(snapItem)) continue;

            if (isPreservedItem(snapItem.getType()) || isForbiddenFromRefill(snapItem.getType()) || !isWhitelisted(snapItem.getType())) {
                ItemStack matched = extractMatchingAmount(currentPool, snapItem);
                newContents[slot] = matched;
            }
        }

        // 2B: Whitelisted items refilled fresh from snapshot
        for (int slot : priorityOrder) {
            if (slot >= snapshot.length) continue;
            ItemStack snapItem = snapshot[slot];
            if (snapItem == null || snapItem.getType() == Material.AIR || isRegearItem(snapItem)) continue;

            if (isWhitelisted(snapItem.getType()) && !isPreservedItem(snapItem.getType()) && !isForbiddenFromRefill(snapItem.getType())) {
                newContents[slot] = snapItem.clone();
                removeFirstMatch(currentPool, snapItem);
            }
        }

        // --- STEP 3: Handle remaining items in currentPool ---
        boolean clearJunk = plugin.getConfig().getBoolean("regear.clear-junk-in-empty-slots", true);

        for (ItemStack leftover : currentPool) {
            if (leftover == null || leftover.getType() == Material.AIR || isRegearItem(leftover)) continue;

            boolean isPreserved = isPreservedItem(leftover.getType());
            boolean isOriginal = isItemInOriginalKit(leftover.getType(), snapshot);

            // Items in preserve-items are NEVER deleted (even when clearJunk = true)
            if (isPreserved || !clearJunk || isOriginal) {
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
                if (leftover.getAmount() > 0) {
                    int emptySlot = findFirstEmptyStorageSlot(newContents);
                    if (emptySlot != -1) {
                        newContents[emptySlot] = leftover;
                    } else if (isPreserved) {
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                    }
                }
            }
        }

        inv.setContents(newContents);
        if (newContents.length > 40) {
            inv.setItemInOffHand(newContents[40]);
        }
        removeAllRegearItems(player);
        player.updateInventory();

        lastRegearTimes.put(uuid, now);

        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
        sendConfigMessage(player, "regear.messages.success", "%seconds%", "0");

        broadcastRegear(player);
        return true;
    }

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
        Component comp = plugin.parseMessage(raw.replace("%player%", player.getName()));

        if (plugin.getConfig().getBoolean("regear.announcement.broadcast", true)) {
            Bukkit.broadcast(comp);
        } else {
            player.sendMessage(comp);
        }
    }

    public boolean isWhitelisted(Material mat) {
        return mat != null && !isForbiddenFromRefill(mat) && !isPreservedItem(mat) && cachedWhitelist.contains(mat);
    }

    private boolean isItemInOriginalKit(Material mat, ItemStack[] snapshot) {
        for (ItemStack item : snapshot) {
            if (item != null && item.getType() == mat && !isRegearItem(item)) {
                return true;
            }
        }
        return false;
    }

    private void sendConfigMessage(Player player, String path, String placeholder, String value) {
        String raw = plugin.getConfig().getString(path);
        if (raw != null && !raw.isEmpty()) {
            player.sendMessage(plugin.parseMessage(raw.replace(placeholder, value)));
        }
    }

    public long getDamageCooldownSec() {
        return damageCooldownSec;
    }

    public long getRegearCooldownSec() {
        return regearCooldownSec;
    }

    public boolean isCheckCombatTag() {
        return checkCombatTag;
    }

    public void clearAll() {
        savedKitSnapshots.clear();
        lastDamageTimes.clear();
        lastRegearTimes.clear();
        cachedWhitelist.clear();
        cachedPreservedItems.clear();
    }
}
