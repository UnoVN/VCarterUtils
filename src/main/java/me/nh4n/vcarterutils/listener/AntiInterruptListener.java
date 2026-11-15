package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.manager.CombatTagManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiInterruptListener implements Listener {

    private final VCarterUtils plugin;
    private final CombatTagManager combatTagManager;
    private final MiniMessage miniMessage;

    // Trackers for indirect player-caused damage sources
    private final Map<UUID, UUID> cartOwners = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> cartActivators = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> crystalActivators = new ConcurrentHashMap<>();

    // Throttled warnings to avoid chat spam during rapid attacks
    private final Map<UUID, Long> lastWarningTimestamps = new ConcurrentHashMap<>();
    private BukkitTask cleanupTask;

    public AntiInterruptListener(VCarterUtils plugin, CombatTagManager combatTagManager) {
        this.plugin = plugin;
        this.combatTagManager = combatTagManager;
        this.miniMessage = MiniMessage.miniMessage();

        startCleanupTask();
    }

    private void startCleanupTask() {
        // Run cleanup every 60 seconds (1200 ticks) to prevent memory accumulation
        this.cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            lastWarningTimestamps.entrySet().removeIf(entry -> now - entry.getValue() > 10000L);
            if (cartOwners.size() > 500) cartOwners.clear();
            if (cartActivators.size() > 500) cartActivators.clear();
            if (crystalActivators.size() > 500) crystalActivators.clear();
        }, 1200L, 1200L);
    }

    public void cancelTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        cartOwners.clear();
        cartActivators.clear();
        crystalActivators.clear();
        lastWarningTimestamps.clear();
    }

    /**
     * Resolves the Player responsible for causing damage.
     */
    private Player resolveAttackingPlayer(Entity damager, EntityDamageEvent event) {
        if (damager instanceof Player p) {
            return p;
        }

        if (damager instanceof Projectile proj) {
            if (proj.getShooter() instanceof Player p) {
                return p;
            }
        }

        if (damager instanceof TNTPrimed tnt) {
            if (tnt.getSource() instanceof Player p) {
                return p;
            }
        }

        if (damager instanceof ExplosiveMinecart cart) {
            UUID actId = cartActivators.get(cart.getUniqueId());
            if (actId != null) {
                Player p = Bukkit.getPlayer(actId);
                if (p != null) return p;
            }
            UUID ownId = cartOwners.get(cart.getUniqueId());
            if (ownId != null) {
                Player p = Bukkit.getPlayer(ownId);
                if (p != null) return p;
            }
        }

        if (damager instanceof EnderCrystal crystal) {
            UUID actId = crystalActivators.get(crystal.getUniqueId());
            if (actId != null) {
                Player p = Bukkit.getPlayer(actId);
                if (p != null) return p;
            }
        }

        if (damager instanceof Firework firework) {
            if (firework.getShooter() instanceof Player p) {
                return p;
            }
        }

        if (damager instanceof AreaEffectCloud aec) {
            if (aec.getSource() instanceof Player p) {
                return p;
            }
        }

        // Paper 1.21.1 DamageSource inspection for any indirect player causation
        if (event != null && event.getDamageSource() != null) {
            Entity causing = event.getDamageSource().getCausingEntity();
            if (causing instanceof Player p) {
                return p;
            }
        }

        return null;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            // Track when an End Crystal is hit by a player or arrow
            if (event.getEntity() instanceof EnderCrystal crystal) {
                Player damagerPlayer = null;
                if (event.getDamager() instanceof Player p) {
                    damagerPlayer = p;
                } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
                    damagerPlayer = p;
                }
                if (damagerPlayer != null) {
                    crystalActivators.put(crystal.getUniqueId(), damagerPlayer.getUniqueId());
                }
            }
            return;
        }

        // Check if Anti-Interrupt is enabled in victim's world
        if (!combatTagManager.isAntiInterruptEnabled(victim.getWorld())) {
            return;
        }

        Player attacker = resolveAttackingPlayer(event.getDamager(), event);

        // Environmental or mob damage -> allow
        if (attacker == null) {
            return;
        }

        // Self-inflicted damage (ender pearl, self rocket, self tnt) -> allow
        if (attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        // Check active duels
        UUID victimOpponent = combatTagManager.getActiveOpponent(victim);
        UUID attackerOpponent = combatTagManager.getActiveOpponent(attacker);

        // Case 1: Victim is in combat with someone else
        if (victimOpponent != null && !victimOpponent.equals(attacker.getUniqueId())) {
            event.setCancelled(true);
            sendThrottledWarning(
                    attacker,
                    "interrupt-blocked",
                    "%prefix_combat%%c_error%<b>ᴅᴜᴇʟ ᴘʀᴏᴛᴇᴄᴛᴇᴅ</b> %c_muted%» %c_text%This target is fighting another player."
            );
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Case 2: Attacker is locked in combat with someone else
        if (attackerOpponent != null && !attackerOpponent.equals(victim.getUniqueId())) {
            event.setCancelled(true);
            sendThrottledWarning(
                    attacker,
                    "combat-restricted",
                    "%prefix_combat%%c_error%<b>ɪɴ ᴄᴏᴍʙᴀᴛ</b> %c_muted%» %c_text%You are locked in combat with your current opponent."
            );
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Allowed: either starting a new 1v1 duel or hitting existing 1v1 opponent
        combatTagManager.recordCombat(attacker, victim);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        // Handled specifically for block explosions (Bed / Respawn Anchor)
        if (event instanceof EntityDamageByEntityEvent) {
            return; // Handled above
        }

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (!combatTagManager.isAntiInterruptEnabled(victim.getWorld())) {
            return;
        }

        if (event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }

        if (event.getDamageSource() == null) {
            return;
        }

        Entity causing = event.getDamageSource().getCausingEntity();
        if (!(causing instanceof Player attacker)) {
            return;
        }

        if (attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        UUID victimOpponent = combatTagManager.getActiveOpponent(victim);
        UUID attackerOpponent = combatTagManager.getActiveOpponent(attacker);

        if (victimOpponent != null && !victimOpponent.equals(attacker.getUniqueId())) {
            event.setCancelled(true);
            sendThrottledWarning(
                    attacker,
                    "interrupt-blocked",
                    "%prefix_combat%%c_error%<b>ᴅᴜᴇʟ ᴘʀᴏᴛᴇᴄᴛᴇᴅ</b> %c_muted%» %c_text%This target is fighting another player."
            );
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (attackerOpponent != null && !attackerOpponent.equals(victim.getUniqueId())) {
            event.setCancelled(true);
            sendThrottledWarning(
                    attacker,
                    "combat-restricted",
                    "%prefix_combat%%c_error%<b>ɪɴ ᴄᴏᴍʙᴀᴛ</b> %c_muted%» %c_text%You are locked in combat with your current opponent."
            );
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        combatTagManager.recordCombat(attacker, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (event.getEntity() instanceof ExplosiveMinecart cart && event.getPlayer() != null) {
            cartOwners.put(cart.getUniqueId(), event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (event.getVehicle() instanceof ExplosiveMinecart cart) {
            Player p = null;
            if (event.getAttacker() instanceof Player player) {
                p = player;
            } else if (event.getAttacker() instanceof Projectile proj && proj.getShooter() instanceof Player player) {
                p = player;
            }
            if (p != null) {
                cartActivators.put(cart.getUniqueId(), p.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (event.getVehicle() instanceof ExplosiveMinecart cart) {
            cartOwners.remove(cart.getUniqueId());
            cartActivators.remove(cart.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player thrower)) {
            return;
        }

        if (!combatTagManager.isAntiInterruptEnabled(thrower.getWorld())) {
            return;
        }

        UUID throwerOpponent = combatTagManager.getActiveOpponent(thrower);

        for (LivingEntity living : event.getAffectedEntities()) {
            if (living instanceof Player victim && !victim.equals(thrower)) {
                UUID victimOpponent = combatTagManager.getActiveOpponent(victim);

                boolean blocked = false;
                if (victimOpponent != null && !victimOpponent.equals(thrower.getUniqueId())) {
                    blocked = true;
                } else if (throwerOpponent != null && !throwerOpponent.equals(victim.getUniqueId())) {
                    blocked = true;
                }

                if (blocked) {
                    event.setIntensity(victim, 0.0);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!(event.getCaught() instanceof Player victim)) {
            return;
        }

        Player fisherman = event.getPlayer();
        if (victim.equals(fisherman)) {
            return;
        }

        if (!combatTagManager.isAntiInterruptEnabled(fisherman.getWorld())) {
            return;
        }

        UUID victimOpponent = combatTagManager.getActiveOpponent(victim);
        UUID fisherOpponent = combatTagManager.getActiveOpponent(fisherman);

        boolean blocked = false;
        if (victimOpponent != null && !victimOpponent.equals(fisherman.getUniqueId())) {
            blocked = true;
            sendThrottledWarning(
                    fisherman,
                    "interrupt-blocked",
                    "%prefix_combat%%c_error%<b>ᴅᴜᴇʟ ᴘʀᴏᴛᴇᴄᴛᴇᴅ</b> %c_muted%» %c_text%This target is fighting another player."
            );
        } else if (fisherOpponent != null && !fisherOpponent.equals(victim.getUniqueId())) {
            blocked = true;
            sendThrottledWarning(
                    fisherman,
                    "combat-restricted",
                    "%prefix_combat%%c_error%<b>ɪɴ ᴄᴏᴍʙᴀᴛ</b> %c_muted%» %c_text%You are locked in combat with your current opponent."
            );
        }

        if (blocked) {
            event.setCancelled(true);
            fisherman.playSound(fisherman.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        combatTagManager.endCombat(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        combatTagManager.endCombat(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        combatTagManager.endCombat(event.getPlayer().getUniqueId());
    }

    private void sendThrottledWarning(Player player, String configKey, String defaultMessage) {
        long now = System.currentTimeMillis();
        Long last = lastWarningTimestamps.get(player.getUniqueId());
        if (last != null && now - last < 1500L) {
            return; // Throttled to 1 message every 1.5s
        }
        lastWarningTimestamps.put(player.getUniqueId(), now);

        String raw = plugin.getConfig().getString("combat-tag.anti-interrupt.messages." + configKey, defaultMessage);
        player.sendMessage(plugin.parseMessage(raw));
    }
}
