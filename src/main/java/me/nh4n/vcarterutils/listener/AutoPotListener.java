package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
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
    private final Map<UUID, BukkitTask> activeScanners = new ConcurrentHashMap<>();
    
    // Ghi nho danh sach loai hieu ung da apply boi AutoPot tu kit truoc cua tung player
    private final Map<UUID, Set<PotionEffectType>> lastAutoPotEffectTypes = new ConcurrentHashMap<>();

    private static final Set<String> NON_LOAD_SUBCOMMANDS = Set.of(
            "room", "editor", "create", "delete", "edit", "swap", "share", "copy", "inspect", "autosetup"
    );

    public AutoPotListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    /**
     * Bat cac lenh load kit cua PerPlayerKit: /k1..k99, /kit1..kit99, /pk, /publickit, /rekit
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().trim();
        if (isKitLoadCommand(msg)) {
            Player player = event.getPlayer();
            scheduleKitProcess(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                scheduleKitProcess(player);
            }
        }, 10L);
    }

    /**
     * Tinh nang: Khi sang world khac, neu trong nguoi co hieu ung vo han thi xoa ngay
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        clearInfiniteEffects(player);

        // Chay them 1 tick de dam bao Paper da dong bo trang thai chuyen world hoan tat
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                clearInfiniteEffects(player);
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        BukkitTask task = activeScanners.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        lastAutoPotEffectTypes.remove(uuid);
    }

    /**
     * Xoa toan bo cac hieu ung khong co thoi gian dem nguoc (vo han)
     */
    private void clearInfiniteEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (isInfiniteEffect(effect)) {
                player.removePotionEffect(effect.getType());
            }
        }
    }

    /**
     * Nhan dien hieu ung vo han:
     * - Paper 1.21 API: effect.isInfinite()
     * - PotionEffect.INFINITE_DURATION (-1)
     * - Thoi gian qua dai do cac plugin spawn/lobby cap (> 1,000,000 tick ~ 14 gio tro len)
     */
    private boolean isInfiniteEffect(PotionEffect effect) {
        if (effect == null) return false;
        try {
            if (effect.isInfinite()) {
                return true;
            }
        } catch (NoSuchMethodError ignored) {
            // Tuong thich nguoc neu core Paper chua cap nhat build moi
        }
        return effect.getDuration() == PotionEffect.INFINITE_DURATION
            || effect.getDuration() == -1
            || effect.getDuration() >= 1_000_000;
    }

    private boolean isKitLoadCommand(String message) {
        if (!message.startsWith("/")) {
            return false;
        }

        String raw = message.substring(1).trim().toLowerCase();
        if (raw.contains(":")) {
            raw = raw.substring(raw.indexOf(':') + 1);
        }

        String[] parts = raw.split("\\s+");
        if (parts.length == 0) {
            return false;
        }

        String cmd = parts[0];

        if (cmd.matches("^k\\d+$") || cmd.matches("^kit\\d+$") ||
            cmd.equals("pk") || cmd.equals("publickit") || cmd.equals("rekit")) {
            return true;
        }

        if ((cmd.equals("k") || cmd.equals("kit")) && parts.length > 1) {
            return !NON_LOAD_SUBCOMMANDS.contains(parts[1]);
        }

        return false;
    }

    /**
     * Cho PerPlayerKit nap xong kho do (sau 5 tick), sau do dong bo hoa va so sanh cac hieu ung
     */
    private void scheduleKitProcess(Player player) {
        UUID uuid = player.getUniqueId();

        BukkitTask oldTask = activeScanners.remove(uuid);
        if (oldTask != null) {
            oldTask.cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    activeScanners.remove(uuid);
                    return;
                }

                // 1. Dong bo hieu ung theo kit moi (giu nguyen cai trung, xoa cai khong co)
                syncKitEffects(player);

                // 2. Chup snapshot layout kit ban dau phuc vu Regear
                plugin.getRegearManager().saveKitSnapshot(player);

                activeScanners.remove(uuid);
            }
        }.runTaskLater(plugin, 5L);

        activeScanners.put(uuid, task);
    }

    /**
     * Logic xu ly AutoPot:
     * - Quet cac lo thuoc trong kit moi.
     * - Neu kit moi KHONG co hieu ung ma nguoi choi dang co / kit cu da apply -> Xoa rieng hieu ung do.
     * - Neu kit moi CO hieu ung do -> Giu nguyen khong xoa.
     */
    private void syncKitEffects(Player player) {
        UUID uuid = player.getUniqueId();
        Map<PotionEffectType, PotionEffect> newKitEffects = extractPotionsFromInventory(player);

        // Tap hop tat ca cac hieu ung can xem xet: gom hieu ung kit cu + hieu ung huu han dang co trong nguoi
        Set<PotionEffectType> currentOrPreviousTypes = new HashSet<>();
        Set<PotionEffectType> prev = lastAutoPotEffectTypes.get(uuid);
        if (prev != null) {
            currentOrPreviousTypes.addAll(prev);
        }

        for (PotionEffect active : player.getActivePotionEffects()) {
            // Bo qua hieu ung vo han de tranh xoa nham buff he thong
            if (!isInfiniteEffect(active)) {
                currentOrPreviousTypes.add(active.getType());
            }
        }

        // BƯỚC 1: XOA RIENG cac effect KHONG ton tai trong kit moi
        for (PotionEffectType type : currentOrPreviousTypes) {
            if (!newKitEffects.containsKey(type)) {
                player.removePotionEffect(type);
            }
        }

        boolean hasAppliedNewEffect = false;

        // BƯỚC 2: GIU NGUYEN hoac REFRESH cac effect CO trong kit moi
        for (PotionEffect newEffect : newKitEffects.values()) {
            PotionEffect current = player.getPotionEffect(newEffect.getType());

            if (current == null) {
                // Chua co -> Apply moi vao nguoi
                player.addPotionEffect(newEffect);
                hasAppliedNewEffect = true;
            } else {
                // Da co san trong nguoi -> GIU NGUYEN!
                // Chi lam moi neu thoi gian con lai cua effect sap het hoac effect moi manh hon
                if (newEffect.getAmplifier() > current.getAmplifier() ||
                   (newEffect.getAmplifier() == current.getAmplifier() && newEffect.getDuration() > current.getDuration())) {
                    player.removePotionEffect(newEffect.getType());
                    player.addPotionEffect(newEffect);
                    hasAppliedNewEffect = true;
                }
            }
        }

        // Cap nhat bo nho luu cac effect cua kit vua load
        lastAutoPotEffectTypes.put(uuid, new HashSet<>(newKitEffects.keySet()));

        if (hasAppliedNewEffect) {
            player.playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 1.2f);
        }
    }

    /**
     * Trich xuat toan bo cac hieu ung co loi tu cac lo thuoc trong kho do
     */
    private Map<PotionEffectType, PotionEffect> extractPotionsFromInventory(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        Map<PotionEffectType, PotionEffect> map = new HashMap<>();

        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            Material type = item.getType();
            if (type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION) {
                if (item.getItemMeta() instanceof PotionMeta meta) {
                    if (meta.hasCustomEffects()) {
                        for (PotionEffect effect : meta.getCustomEffects()) {
                            mergeEffect(map, effect);
                        }
                    }

                    PotionType baseType = meta.getBasePotionType();
                    if (baseType != null) {
                        for (PotionEffect effect : baseType.getPotionEffects()) {
                            mergeEffect(map, effect);
                        }
                    }
                }
            }
        }
        return map;
    }

    private void mergeEffect(Map<PotionEffectType, PotionEffect> map, PotionEffect effect) {
        if (isHarmful(effect.getType())) {
            return;
        }

        PotionEffect existing = map.get(effect.getType());
        if (existing == null) {
            map.put(effect.getType(), effect);
        } else {
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
        lastAutoPotEffectTypes.clear();
    }
}
