package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AutoPotListener implements Listener {

    private final VCarterUtils plugin;
    // Quan ly task scanner dang chay de tranh spam khi player go nhieu lenh lien tiep
    private final Map<UUID, BukkitTask> activeScanners = new ConcurrentHashMap<>();

    // Cac lenh quan ly kit KHONG PHAI la lenh load kit
    private static final Set<String> NON_LOAD_SUBCOMMANDS = Set.of(
            "room", "editor", "create", "delete", "edit", "swap", "share", "copy", "inspect", "autosetup"
    );

    public AutoPotListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().trim();
        // Chi kich hoat khi player thuc su chay lenh load kit
        if (isKitLoadCommand(msg)) {
            schedulePotionScanner(event.getPlayer());
        }
    }

    /**
     * Kiem tra chinh xac xem lenh go co phai la lenh load kit hay khong:
     * - /k1 .. /k99, /kit1 .. /kit99
     * - /k <slot|name>, /kit <slot|name>, /k load <slot>
     * - /pk <name>, /publickit <name>
     * - /rekit
     * - Khong bat lenh chi go /kit hoac /k (chi mo menu)
     */
    private boolean isKitLoadCommand(String message) {
        if (!message.startsWith("/")) {
            return false;
        }

        String raw = message.substring(1).trim().toLowerCase();
        // Loai bo namespace neu co (vi du: /perplayerkit:k1 -> k1)
        if (raw.contains(":")) {
            raw = raw.substring(raw.indexOf(':') + 1);
        }

        String[] parts = raw.split("\\s+");
        if (parts.length == 0) {
            return false;
        }

        String cmd = parts[0];

        // 1. /k1..k99 hoac /kit1..kit99 (Lenh load kit chinh cua PerPlayerKit)
        if (cmd.matches("^k\\d+$") || cmd.matches("^kit\\d+$")) {
            return true;
        }

        // 2. /pk <name> hoac /publickit <name> (Lenh load public kit)
        if (cmd.equals("pk") || cmd.equals("publickit")) {
            return true;
        }

        // 3. /rekit
        if (cmd.equals("rekit")) {
            return true;
        }

        // 4. /k <arg> hoac /kit <arg> co tham so (vi du: /k 1, /kit 1, /k load 1, /kit pvp)
        if ((cmd.equals("k") || cmd.equals("kit")) && parts.length > 1) {
            String sub = parts[1];
            return !NON_LOAD_SUBCOMMANDS.contains(sub);
        }

        return false;
    }

    /**
     * Quet kho do sau 4 tick (0.2s) de doi PerPlayerKit nap xong do vao tui,
     * neu chua kip nạp se thu lai toi da 5 lan (moi 4 tick mot lan).
     */
    private void schedulePotionScanner(Player player) {
        UUID uuid = player.getUniqueId();

        // Huy task cu neu co lenh moi den lien tiep
        BukkitTask oldTask = activeScanners.remove(uuid);
        if (oldTask != null) {
            oldTask.cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            int runs = 0;

            @Override
            public void run() {
                if (!player.isOnline() || runs >= 5) {
                    activeScanners.remove(uuid);
                    cancel();
                    return;
                }

                boolean applied = applyPotionsFromInventory(player);
                if (applied) {
                    activeScanners.remove(uuid);
                    cancel(); // Da nạp va apply thanh cong -> huy task ngay
                    return;
                }

                runs++;
            }
        }.runTaskTimer(plugin, 4L, 4L);

        activeScanners.put(uuid, task);
    }

    private boolean applyPotionsFromInventory(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        Map<PotionEffectType, PotionEffect> effectsToApply = new HashMap<>();

        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            Material type = item.getType();
            if (type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION) {
                if (item.getItemMeta() instanceof PotionMeta meta) {

                    // 1. Lay custom effects tu kit
                    if (meta.hasCustomEffects()) {
                        for (PotionEffect effect : meta.getCustomEffects()) {
                            mergeEffect(effectsToApply, effect);
                        }
                    }

                    // 2. Lay base effects (Speed, Strength, Fire Resistance...)
                    PotionType baseType = meta.getBasePotionType();
                    if (baseType != null) {
                        for (PotionEffect effect : baseType.getPotionEffects()) {
                            mergeEffect(effectsToApply, effect);
                        }
                    }

                    // KHONG XOA LO THUOC: Giu nguyen toan bo lo thuoc trong kho do
                }
            }
        }

        if (effectsToApply.isEmpty()) {
            return false;
        }

        // Apply tat ca hieu ung vao nguoi choi (da loc bo trung lap va loc hieu ung gay hai)
        for (PotionEffect effect : effectsToApply.values()) {
            player.removePotionEffect(effect.getType());
            player.addPotionEffect(effect);
        }

        // Phat am thanh 1 lan duy nhat khi nạp kit thanh cong
        player.playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 1.2f);
        return true;
    }

    private void mergeEffect(Map<PotionEffectType, PotionEffect> map, PotionEffect effect) {
        if (isHarmful(effect.getType())) {
            return;
        }

        PotionEffect existing = map.get(effect.getType());
        if (existing == null) {
            map.put(effect.getType(), effect);
        } else {
            // Uu tien cap do cao hon hoac thoi gian lau hon
            if (effect.getAmplifier() > existing.getAmplifier() ||
               (effect.getAmplifier() == existing.getAmplifier() && effect.getDuration() > existing.getDuration())) {
                map.put(effect.getType(), effect);
            }
        }
    }

    private boolean isHarmful(PotionEffectType type) {
        return type == PotionEffectType.INSTANT_DAMAGE ||
               type == PotionEffectType.POISON ||
               type == PotionEffectType.SLOWNESS ||
               type == PotionEffectType.WEAKNESS ||
               type == PotionEffectType.WITHER ||
               type == PotionEffectType.BLINDNESS ||
               type == PotionEffectType.DARKNESS ||
               type == PotionEffectType.NAUSEA ||
               type == PotionEffectType.MINING_FATIGUE ||
               type == PotionEffectType.HUNGER ||
               type == PotionEffectType.LEVITATION;
    }

    public void clearAll() {
        for (BukkitTask task : activeScanners.values()) {
            task.cancel();
        }
        activeScanners.clear();
    }
}
