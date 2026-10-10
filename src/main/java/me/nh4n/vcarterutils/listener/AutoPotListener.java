package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import org.bukkit.Bukkit;
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
import org.bukkit.potion.PotionType;

import java.util.List;

public class AutoPotListener implements Listener {

    private final VCarterUtils plugin;

    public AutoPotListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKitCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().trim().toLowerCase();

        // Kiem tra cu phap lenh nhan kit: /kit, /kit <ten>, /k, /k <ten>
        if (!message.equals("/kit") && !message.startsWith("/kit ") &&
            !message.equals("/k") && !message.startsWith("/k ")) {
            return;
        }

        Player player = event.getPlayer();

        // Delay 2 tick de PerPlayerKit nap vat pham vao inventory
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            ItemStack[] inventoryContents = player.getInventory().getContents();
            boolean potionApplied = false;

            for (ItemStack item : inventoryContents) {
                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }

                Material itemType = item.getType();
                // Nhan dien moi loai thuoc: thuoc uong, splash potion, lingering potion
                if (itemType == Material.POTION || itemType == Material.SPLASH_POTION || itemType == Material.LINGERING_POTION) {
                    if (item.getItemMeta() instanceof PotionMeta meta) {

                        // 1. Ap dung custom potion effects neu kit co setup hieu ung tuy bien
                        if (meta.hasCustomEffects()) {
                            for (PotionEffect effect : meta.getCustomEffects()) {
                                player.addPotionEffect(effect);
                                potionApplied = true;
                            }
                        }

                        // 2. Ap dung hieu ung tu Potion Type goc (Speed, Strength, Fire Resistance...)
                        PotionType baseType = meta.getBasePotionType();
                        if (baseType != null) {
                            List<PotionEffect> baseEffects = baseType.getPotionEffects();
                            for (PotionEffect effect : baseEffects) {
                                player.addPotionEffect(effect);
                                potionApplied = true;
                            }
                        }

                        // KHONG XOA VAT PHAM: Toan bo binh thuoc van giu nguyen ven trong tui do
                    }
                }
            }

            // Phat am thanh thong bao hieu ung da vao nguoi
            if (potionApplied) {
                player.playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 1.2f);
            }
        }, 2L);
    }
}
