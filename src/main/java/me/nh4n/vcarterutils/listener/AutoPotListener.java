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
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AutoPotListener implements Listener {

    private final VCarterUtils plugin;

    // Regex bat tron moi lenh load kit cua PerPlayerKit: /k1../k99, /kit1../kit99, /kit, /k, /publickit, /pk, /rekit, /rg, /regear
    private static final Pattern KIT_COMMAND_PATTERN = Pattern.compile(
            "^/(?:[a-zA-Z0-9_]+:)?(?:k|kit|pk|publickit|rekit|regear|rg)(?:\\d+)?(?:\\s+.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    public AutoPotListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().trim();
        if (KIT_COMMAND_PATTERN.matcher(msg).matches()) {
            schedulePotionScanner(event.getPlayer());
        }
    }

    // Bat ca khi su kien bi PerPlayerKit cancel de chong trom icon GUI
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            // Khi player click trong bat ky GUI nao (menu /kit, /k)
            if (event.getView().getTopInventory().getSize() > 0) {
                schedulePotionScanner(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            schedulePotionScanner(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Ho tro che do Rekit-on-respawn cua PerPlayerKit
        schedulePotionScanner(event.getPlayer());
    }

    /**
     * Quet kho do theo chu ky tre (5, 10, 15, 20, 25 ticks) de dam bao 
     * PerPlayerKit da nap item va da chay xong kits.load.clear-effects.
     */
    private void schedulePotionScanner(Player player) {
        new BukkitRunnable() {
            int runs = 0;

            @Override
            public void run() {
                if (!player.isOnline() || runs >= 5) {
                    cancel();
                    return;
                }

                boolean applied = applyPotionsFromInventory(player);
                if (applied) {
                    cancel(); // Da tim thay va apply thanh cong -> dung quet
                    return;
                }

                runs++;
            }
        }.runTaskTimer(plugin, 5L, 5L);
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
                    List<PotionEffect> effectsToApply = new ArrayList<>();

                    // 1. Thu thap custom effects neu kit co cau hinh dac biet
                    if (meta.hasCustomEffects()) {
                        effectsToApply.addAll(meta.getCustomEffects());
                    }

                    // 2. Thu thap effects tu Base Potion Type goc (Speed, Strength, Fire Resistance...)
                    PotionType baseType = meta.getBasePotionType();
                    if (baseType != null) {
                        effectsToApply.addAll(baseType.getPotionEffects());
                    }

                    // 3. Loc bo cac hieu ung gay hai (tranh bi dinh Poison, Damage khi kit co pot nem combat)
                    for (PotionEffect effect : effectsToApply) {
                        if (isHarmful(effect.getType())) {
                            continue;
                        }

                        // Xoa hieu ung cu de dam bao Paper ap dung / lam moi duration
                        player.removePotionEffect(effect.getType());
                        player.addPotionEffect(effect);
                        hasAppliedAny = true;
                    }

                    // GIU NGUYEN LO THUOC TRONG INVENTORY: Khong xoa hay set null
                }
            }
        }

        if (hasAppliedAny) {
            player.playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 1.2f);
        }

        return hasAppliedAny;
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
               type == PotionEffectType.HUNGER;
    }
}
