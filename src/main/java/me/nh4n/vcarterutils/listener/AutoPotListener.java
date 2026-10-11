package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
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
    private final Map<UUID, Long> lastRekitAnnounceTimes = new ConcurrentHashMap<>();

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
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                scheduleKitProcess(player);
            }
        }, 10L);
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

    private void scheduleKitProcess(Player player) {
        UUID uuid = player.getUniqueId();

        BukkitTask oldTask = activeScanners.remove(uuid);
        if (oldTask != null) {
            oldTask.cancel();
        }

        broadcastRekit(player);

        BukkitTask task = new BukkitRunnable() {
            int runs = 0;

            @Override
            public void run() {
                if (!player.isOnline() || runs >= 5) {
                    activeScanners.remove(uuid);
                    plugin.getRegearManager().saveKitSnapshot(player);
                    cancel();
                    return;
                }

                boolean applied = applyPotionsFromInventory(player);
                if (applied) {
                    activeScanners.remove(uuid);
                    plugin.getRegearManager().saveKitSnapshot(player);
                    cancel();
                    return;
                }

                runs++;
            }
        }.runTaskTimer(plugin, 4L, 4L);

        activeScanners.put(uuid, task);
    }

    private void broadcastRekit(Player player) {
        if (!plugin.getConfig().getBoolean("kit.announcement.enabled", true)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastAnnounce = lastRekitAnnounceTimes.get(player.getUniqueId());
        if (lastAnnounce != null && (now - lastAnnounce < 2500L)) {
            return;
        }
        lastRekitAnnounceTimes.put(player.getUniqueId(), now);

        String rawMsg = plugin.getConfig().getString(
                "kit.announcement.message",
                "<glyph:warning_blue> <#89dceb><b>%player%</b> <#a6adc8>rekited"
        );

        Component comp = MiniMessage.miniMessage().deserialize(rawMsg.replace("%player%", player.getName()));

        if (plugin.getConfig().getBoolean("kit.announcement.broadcast", true)) {
            Bukkit.broadcast(comp);
        } else {
            player.sendMessage(comp);
        }
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
                    if (meta.hasCustomEffects()) {
                        for (PotionEffect effect : meta.getCustomEffects()) {
                            mergeEffect(effectsToApply, effect);
                        }
                    }

                    PotionType baseType = meta.getBasePotionType();
                    if (baseType != null) {
                        for (PotionEffect effect : baseType.getPotionEffects()) {
                            mergeEffect(effectsToApply, effect);
                        }
                    }
                }
            }
        }

        if (effectsToApply.isEmpty()) {
            return false;
        }

        for (PotionEffect effect : effectsToApply.values()) {
            player.removePotionEffect(effect.getType());
            player.addPotionEffect(effect);
        }

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
        lastRekitAnnounceTimes.clear();
    }
}
