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
     * Handles player attacks using a Mace:
     * 1. If Mace is currently on cooldown -> Cancels the attack (if cancel-attack-on-cooldown is enabled).
     * 2. If Mace is not on cooldown -> Allows hit and applies native Vanilla cooldown overlay.
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

        // 1. Check if attacker's Mace is on cooldown
        int remainingTicks = attacker.getCooldown(Material.MACE);
        if (remainingTicks > 0) {
            if (manager.isCancelAttackOnCooldown()) {
                event.setCancelled(true);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                manager.sendOnCooldownWarning(attacker, remainingTicks);
            }
            return;
        }

        // 2. If smash-attack-only is enabled, verify fall distance (> 1.5 blocks)
        if (manager.isOnlySmashAttack() && !isSmashAttack(attacker)) {
            return;
        }

        // 3. Calculate and apply Mace cooldown
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
