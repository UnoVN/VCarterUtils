package me.nh4n.vcarterutils.listener;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.hook.FoCombatTagHook;
import me.nh4n.vcarterutils.manager.CombatTagManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.lang.reflect.Method;

public class CombatTagListener implements Listener {

    private final VCarterUtils plugin;
    private final CombatTagManager combatTagManager;
    private final MiniMessage miniMessage;

    public CombatTagListener(VCarterUtils plugin, CombatTagManager combatTagManager) {
        this.plugin = plugin;
        this.combatTagManager = combatTagManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Registers this listener and hooks dynamically into FoCombatTag's CombatTagApplyEvent.
     */
    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        hookFoCombatTagApplyEvent();
    }

    private void hookFoCombatTagApplyEvent() {
        try {
            Class<?> eventClass = Class.forName("me.foesio.foCombatTag.api.event.CombatTagApplyEvent");
            Class<? extends Event> bukkitEventClass = eventClass.asSubclass(Event.class);
            Method getPlayerMethod = eventClass.getMethod("getPlayer");
            getPlayerMethod.setAccessible(true);
            Method setDurationMethod = eventClass.getMethod("setDurationSeconds", int.class);
            setDurationMethod.setAccessible(true);
            Method setCancelledMethod = eventClass.getMethod("setCancelled", boolean.class);
            setCancelledMethod.setAccessible(true);

            Method getCauseMethod = null;
            Method getOpponentNameMethod = null;
            try {
                getCauseMethod = eventClass.getMethod("getCause");
                getCauseMethod.setAccessible(true);
            } catch (NoSuchMethodException ignored) {}
            try {
                getOpponentNameMethod = eventClass.getMethod("getOpponentName");
                getOpponentNameMethod.setAccessible(true);
            } catch (NoSuchMethodException ignored) {}

            final Method finalGetCause = getCauseMethod;
            final Method finalGetOpponentName = getOpponentNameMethod;

            Bukkit.getPluginManager().registerEvent(
                    bukkitEventClass,
                    this,
                    EventPriority.HIGHEST,
                    (listener, event) -> {
                        try {
                            Player player = (Player) getPlayerMethod.invoke(event);
                            if (player == null || !player.isOnline()) {
                                return;
                            }

                            String causeName = "";
                            if (finalGetCause != null) {
                                Object causeObj = finalGetCause.invoke(event);
                                if (causeObj != null) {
                                    causeName = causeObj.toString();
                                }
                            }

                            String opponentName = "";
                            if (finalGetOpponentName != null) {
                                Object oppObj = finalGetOpponentName.invoke(event);
                                if (oppObj != null) {
                                    opponentName = oppObj.toString();
                                }
                            }

                            Player realOpponent = (!opponentName.isEmpty()) ? Bukkit.getPlayerExact(opponentName) : null;
                            boolean isRealPvP = realOpponent != null && realOpponent.isOnline() && !realOpponent.getUniqueId().equals(player.getUniqueId());

                            // If player is in Training Mode:
                            if (plugin.getTrainingManager() != null && plugin.getTrainingManager().isPlayerEnabled(player.getUniqueId())) {
                                if (isRealPvP) {
                                    // Real PvP combat with another distinct online player: disable training mode
                                    plugin.getTrainingManager().disableTrainingOnCombat(player);
                                } else {
                                    // Pearl, wind charge, self-damage, or practice: cancel combat tag and KEEP training mode ON!
                                    if (event instanceof org.bukkit.event.Cancellable cancellable) {
                                        cancellable.setCancelled(true);
                                    }
                                    setCancelledMethod.invoke(event, true);
                                    FoCombatTagHook.untag(player);
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        if (player.isOnline() && FoCombatTagHook.isTagged(player)) {
                                            FoCombatTagHook.untag(player);
                                        }
                                    });
                                    return;
                                }
                            }

                            if (!combatTagManager.isEnabled()) {
                                return;
                            }

                            int effectiveSeconds = combatTagManager.getEffectiveCombatSeconds(player.getWorld());
                            if (effectiveSeconds == 0) {
                                // 0 seconds means combat tagging is completely disabled in this world
                                setCancelledMethod.invoke(event, true);
                            } else if (effectiveSeconds > 0) {
                                // Custom duration forced for this world
                                setDurationMethod.invoke(event, effectiveSeconds);
                            }
                        } catch (Throwable ignored) {
                            // Suppressed to eliminate console spam
                        }
                    },
                    plugin
            );
            plugin.getLogger().info("Successfully hooked into FoCombatTag CombatTagApplyEvent for per-world timers.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("FoCombatTag not detected; skipping per-world CombatTagApplyEvent hook.");
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to hook FoCombatTag CombatTagApplyEvent: " + t.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!combatTagManager.isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        // If entering a world where combat tagging is disabled, clear combat tag immediately
        if (combatTagManager.getEffectiveCombatSeconds(player.getWorld()) == 0) {
            if (FoCombatTagHook.isTagged(player)) {
                FoCombatTagHook.untag(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!combatTagManager.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("vcarterutils.bypass.combatcommands") || player.hasPermission("vcarterutils.admin")) {
            return;
        }

        if (!FoCombatTagHook.isTagged(player)) {
            return;
        }

        // Special handling for /back: NEVER block /back command in combat tag listener
        String msgLower = event.getMessage().trim().toLowerCase();
        if (msgLower.equals("/back") || msgLower.startsWith("/back ")
                || msgLower.equals("/deathback") || msgLower.startsWith("/deathback ")
                || msgLower.equals("/return") || msgLower.startsWith("/return ")
                || msgLower.equals("/vcarterutils:back") || msgLower.startsWith("/vcarterutils:back ")) {
            return;
        }

        if (combatTagManager.isCommandBlocked(player.getWorld(), event.getMessage())) {
            event.setCancelled(true);

            long remainingSec = FoCombatTagHook.getRemainingSeconds(player);
            String rawMessage = plugin.getConfig().getString(
                    "combat-tag.messages.command-blocked",
                    "%prefix_combat%%c_error%<b>ɪɴ ᴄᴏᴍʙᴀᴛ</b> %c_muted%» %c_text%Command blocked for %c_warning%<b>%seconds%s</b>%c_text%."
            );

            player.sendMessage(plugin.parseMessage(rawMessage.replace("%seconds%", String.valueOf(remainingSec))));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }
}
