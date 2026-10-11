package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoPotListener implements Listener {

    private final VCarterUtils plugin;
    // Luu thoi gian goi kit gan nhat de chong spam va ho tro bat su kien GUI
    private final Map<UUID, Long> lastKitClaimTimes = new ConcurrentHashMap<>();

    public AutoPotListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST) // Khong dung ignoreCancelled de luon bat duoc lenh
    public void onKitCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().trim().toLowerCase();
        String cmd = raw.split(" ")[0];

        // Ho tro moi bien the: /kit, /k, /rekit, /ppk, /perplayerkit
        if (cmd.equals("/kit") || cmd.equals("/k") || cmd.equals("/rekit") ||
            cmd.equals("/ppk") || cmd.endsWith(":kit") || cmd.endsWith(":k")) {
            
            Player player = event.getPlayer();
            lastKitClaimTimes.put(player.getUniqueId(), System.currentTimeMillis());
            schedulePotionScanner(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Kiem tra neu nguoi choi vua go lenh kit trong vong 10 giay ma click vao menu GUI
        Long lastCmd = lastKitClaimTimes.get(player.getUniqueId());
        if (lastCmd != null && System.currentTimeMillis() - lastCmd < 10000L) {
            schedulePotionScanner(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Long lastCmd = lastKitClaimTimes.get(player.getUniqueId());
        if (lastCmd != null && System.currentTimeMillis() - lastCmd < 10000L) {
            schedulePotionScanner(player);
        }
    }

    /**
     * Quet kho do theo chu ky 2 tick, 5 tick, 10 tick, 20 tick 
     * de dam bao bat duoc pot ngay khi PerPlayerKit hoan tat nap item.
     */
    private void schedulePotionScanner(Player player) {
        new BukkitRunnable() {
            int attempts = 0;

            @Override
            public void run() {
                if (!player.isOnline() || attempts >= 4) {
                    cancel();
                    return;
                }

                boolean applied = applyPotionsFromInventory(player);
                if (applied) {
                    cancel(); // Da tim thay va apply thanh cong -> dung quet
                    return;
                }

                attempts++;
            }
        }.runTaskTimer(plugin, 2L, 5L);
    }

    private boolean applyPotionsFromInventory(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        boolean hasAppliedAny = false;

        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            Material type = item.getType();
            if (type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION) {
                if (item.getItemMeta() instanceof PotionMeta meta) {

                    // 1. Ap dung Custom Effects
                    if (meta.hasCustomEffects()) {
                        for (PotionEffect effect : meta.getCustomEffects()) {
                            // Xoa effect cu truoc khi add de tranh bi Bukkit tu choi ghi de
                            player.removePotionEffect(effect.getType());
                            player.addPotionEffect(effect);
                            hasAppliedAny = true;
                        }
                    }

                    // 2. Ap dung Base Potion Effects (Speed, Strength, Fire Resistance...)
                    PotionType baseType = meta.getBasePotionType();
                    if (baseType != null) {
                        List<PotionEffect> baseEffects = baseType.getPotionEffects();
                        for (PotionEffect effect : baseEffects) {
                            player.removePotionEffect(effect.getType());
                            player.addPotionEffect(effect);
                            hasAppliedAny = true;
                        }
                    }

                    // KHONG XOA POTION: Giu nguyen toan bo binh thuoc trong inventory
                }
            }
        }

        if (hasAppliedAny) {
            player.playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 1.2f);
        }

        return hasAppliedAny;
    }
}
