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
        // Reset luot kich hoat totem khi chet va hoi sinh
        plugin.getTotemManager().resetActivations(player.getUniqueId());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                scheduleKitProcess(player);
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        clearInfiniteEffects(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                clearInfiniteEffects(player);
                plugin.getTotemManager().enforceInventoryLimit(player);
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

    private boolean isAutoPotEnabledFor(Player player) {
        if (!plugin.getConfig().getBoolean("autopot.enabled", true)) {
            return false;
        }
        if (player == null || player.getWorld() == null) {
            return false;
        }

        String currentWorld = player.getWorld().getName();
        List<String> blacklistedWorlds = plugin.getConfig().getStringList("autopot.blacklisted-worlds");
        for (String blacklisted : blacklistedWorlds) {
            if (blacklisted != null && blacklisted.trim().equalsIgnoreCase(currentWorld.trim())) {
                return false;
            }
        }
        return true;
    }

    private void clearInfiniteEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (isInfiniteEffect(effect)) {
                player.removePotionEffect(effect.getType());
            }
        }
    }

    private boolean isInfiniteEffect(PotionEffect effect) {
        if (effect == null) return false;
        try {
            if (effect.isInfinite()) {
                return true;
            }
        } catch (NoSuchMethodError ignored) {
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
     * Cho PerPlayerKit nap xong kho do (sau 5 tick):
     * 1. Reset luot dung Totem, xoa Pearl & Mace Cooldown (neu bat) va kiem tra gioi han Totem.
     * 2. Kiem tra blacklist world cua AutoPot va apply effect.
     * 3. Chup snapshot layout kit ban dau cho he thong Regear.
     */
    private void scheduleKitProcess(Player player) {
        UUID uuid = player.getUniqueId();

        BukkitTask oldTask = activeScanners.remove(uuid);
        if (oldTask != null) {
            oldTask.cancel();
        }

        // RESET LUOT DUNG TOTEM KHI LOAD/REKIT
        plugin.getTotemManager().resetActivations(uuid);

        // XOA COOLDOWN ENDER PEARL KHI LOAD KIT NEU DUOC BAT
        if (plugin.getPearlManager().isEnabled() && plugin.getPearlManager().isClearOnKitLoad()) {
            plugin.getPearlManager().clearCooldown(player);
        }

        // XOA COOLDOWN MACE KHI LOAD KIT NEU DUOC BAT
        if (plugin.getMaceManager().isEnabled() && plugin.getMaceManager().isClearOnKitLoad()) {
            plugin.getMaceManager().clearCooldown(player);
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    activeScanners.remove(uuid);
                    return;
                }

                // Xu ly gioi han Totem trong kho do ngay sau khi kit nap xong
                plugin.getTotemManager().enforceInventoryLimit(player);

                // Kiem tra blacklist world: chi apply AutoPot neu world duoc phep
                if (isAutoPotEnabledFor(player)) {
                    syncKitEffects(player);
                }

                // Chup snapshot layout kit ban dau phuc vu Regear
                plugin.getRegearManager().saveKitSnapshot(player);

                activeScanners.remove(uuid);
            }
        }.runTaskLater(plugin, 5L);

        activeScanners.put(uuid, task);
    }

    private void syncKitEffects(Player player) {
        UUID uuid = player.getUniqueId();
        Map<PotionEffectType, PotionEffect> newKitEffects = extractPotionsFromInventory(player);

        Set<PotionEffectType> currentOrPreviousTypes = new HashSet<>();
        Set<PotionEffectType> prev = lastAutoPotEffectTypes.get(uuid);
        if (prev != null) {
            currentOrPreviousTypes.addAll(prev);
        }

        for (PotionEffect active : player.getActivePotionEffects()) {
            if (!isInfiniteEffect(active)) {
                currentOrPreviousTypes.add(active.getType());
            }
        }

        for (PotionEffectType type : currentOrPreviousTypes) {
            if (!newKitEffects.containsKey(type)) {
                player.removePotionEffect(type);
            }
        }

        boolean hasAppliedNewEffect = false;

        for (PotionEffect newEffect : newKitEffects.values()) {
            PotionEffect current = player.getPotionEffect(newEffect.getType());

            if (current == null) {
                player.addPotionEffect(newEffect);
                hasAppliedNewEffect = true;
            } else {
                if (newEffect.getAmplifier() > current.getAmplifier() ||
                   (newEffect.getAmplifier() == current.getAmplifier() && newEffect.getDuration() > current.getDuration())) {
                    player.removePotionEffect(newEffect.getType());
                    player.addPotionEffect(newEffect);
                    hasAppliedNewEffect = true;
                }
            }
        }

        lastAutoPotEffectTypes.put(uuid, new HashSet<>(newKitEffects.keySet()));

        if (hasAppliedNewEffect) {
            player.playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 1.2f);
        }
    }

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
