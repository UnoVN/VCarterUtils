package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

    // Snapshot layout goc cua tung nguoi choi
    private final Map<UUID, ItemStack[]> savedKitSnapshots = new ConcurrentHashMap<>();
    // Combat tag
    private final Map<UUID, Long> lastDamageTimes = new ConcurrentHashMap<>();
    // Regear cooldown
    private final Map<UUID, Long> lastRegearTimes = new ConcurrentHashMap<>();

    public RegearManager(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
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

        // 2. Kiem tra Regear Cooldown
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

        // 4. Loc Whitelist va de dung vi tri tung slot
        Set<Material> whitelist = getWhitelistedMaterials();
        PlayerInventory inv = player.getInventory();

        for (int slot = 0; slot < snapshot.length; slot++) {
            // Bo qua cac slot giap (36, 37, 38, 39)
            if (slot >= 36 && slot <= 39) {
                continue;
            }

            ItemStack originalItem = snapshot[slot];
            if (originalItem == null || originalItem.getType() == Material.AIR) {
                continue;
            }

            if (whitelist.contains(originalItem.getType())) {
                ItemStack currentItem = inv.getItem(slot);

                // Neu o trong, bi chen boi rac/vat pham khac, hoac thieu so luong -> De truc tiep
                if (currentItem == null || currentItem.getType() != originalItem.getType() || currentItem.getAmount() < originalItem.getAmount()) {
                    if (slot == 40) {
                        inv.setItemInOffHand(originalItem.clone());
                    } else {
                        inv.setItem(slot, originalItem.clone());
                    }
                }
            }
        }

        lastRegearTimes.put(uuid, now);

        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
        sendConfigMessage(player, "regear.messages.success", "%seconds%", "0");

        return true;
    }

    private Set<Material> getWhitelistedMaterials() {
        List<String> list = plugin.getConfig().getStringList("regear.whitelist");
        Set<Material> set = EnumSet.noneOf(Material.class);

        for (String entry : list) {
            String clean = entry.trim().toUpperCase();
            if (clean.isEmpty()) continue;

            Material mat = Material.matchMaterial(clean);
            if (mat != null) {
                set.add(mat);
            }

            if (clean.equals("FIREWORK") || clean.equals("FIREWORKS")) {
                set.add(Material.FIREWORK_ROCKET);
            } else if (clean.equals("WEB")) {
                set.add(Material.COBWEB);
            } else if (clean.equals("PLANKS") || clean.contains("PLANK")) {
                for (Material m : Material.values()) {
                    if (m.name().endsWith("_PLANKS")) {
                        set.add(m);
                    }
                }
            } else if (clean.equals("RAIL") || clean.equals("RAILS")) {
                for (Material m : Material.values()) {
                    if (m.name().endsWith("_RAIL")) {
                        set.add(m);
                    }
                }
            } else if (clean.equals("WOOD") || clean.equals("LOGS") || clean.equals("WOODS")) {
                for (Material m : Material.values()) {
                    if (m.name().endsWith("_LOG") || m.name().endsWith("_WOOD") || m.name().endsWith("_STEM")) {
                        set.add(m);
                    }
                }
            }
        }
        return set;
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
