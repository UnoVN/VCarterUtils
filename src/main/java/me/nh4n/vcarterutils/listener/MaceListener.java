package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.MaceManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

public class MaceListener implements Listener {

    private final VCarterUtils plugin;

    public MaceListener(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    /**
     * Xu ly khi nguoi choi tan cong bang Mace:
     * 1. Neu Mace dang trong thoi gian cooldown -> Huy don danh (neu bat cancel-attack-on-cooldown).
     * 2. Neu Mace chua cooldown -> Cho phep danh trung va dat goi tin Cooldown Vanilla (lop phu trang).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaceAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (mainHand.getType() != Material.MACE) {
            return;
        }

        MaceManager manager = plugin.getMaceManager();
        if (!manager.isEnabled()) {
            return;
        }

        if (manager.isOnlyPlayers() && !(victim instanceof Player)) {
            return;
        }

        // 1. Kiem tra xem Mace cua nguoi choi co dang trong thoi gian hoi chieu hay khong
        int remainingTicks = attacker.getCooldown(Material.MACE);
        if (remainingTicks > 0) {
            if (manager.isCancelAttackOnCooldown()) {
                event.setCancelled(true);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                manager.sendOnCooldownWarning(attacker, remainingTicks);
            }
            return;
        }

        // 2. Neu bat che do chi tinh cooldown khi Smash Attack (roi > 1.5 block)
        if (manager.isOnlySmashAttack() && !isSmashAttack(attacker)) {
            return;
        }

        // 3. Tinh toan va ap dung Cooldown cho Mace
        int cooldownSeconds = manager.getEffectiveCooldownSeconds(attacker);
        int cooldownTicks = Math.max(0, cooldownSeconds * 20);

        if (cooldownTicks > 0) {
            attacker.setCooldown(Material.MACE, cooldownTicks);
        }
    }

    private boolean isSmashAttack(Player player) {
        return player.getFallDistance() > 1.5f
                && !player.isGliding()
                && !player.isClimbing()
                && !player.isInWater();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        MaceManager manager = plugin.getMaceManager();
        if (manager.isEnabled() && manager.isClearOnRespawn()) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    manager.clearCooldown(player);
                }
            }, 2L);
        }
    }
}
