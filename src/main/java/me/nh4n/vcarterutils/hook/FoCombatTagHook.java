package me.nh4n.vcarterutils.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Optional;

public final class FoCombatTagHook {

    private static boolean reflectionChecked = false;
    private static Method isAvailableMethod;
    private static Method getMethod;
    private static Method isTaggedPlayerMethod;
    private static Method getRemainingSecondsMethod;
    private static Method untagPlayerMethod;

    private FoCombatTagHook() {
    }

    private static synchronized void initReflection() {
        if (reflectionChecked && isAvailableMethod != null) {
            return;
        }
        reflectionChecked = true;
        try {
            Class<?> apiClass = Class.forName("me.foesio.foCombatTag.api.FoCombatTagApi");
            isAvailableMethod = apiClass.getMethod("isAvailable");
            isAvailableMethod.setAccessible(true);
            getMethod = apiClass.getMethod("get");
            getMethod.setAccessible(true);
            isTaggedPlayerMethod = apiClass.getMethod("isTagged", Player.class);
            isTaggedPlayerMethod.setAccessible(true);
            getRemainingSecondsMethod = apiClass.getMethod("getRemainingSeconds", Player.class);
            getRemainingSecondsMethod.setAccessible(true);
            untagPlayerMethod = apiClass.getMethod("untag", Player.class);
            untagPlayerMethod.setAccessible(true);
        } catch (Throwable ignored) {
            isAvailableMethod = null;
            getMethod = null;
            isTaggedPlayerMethod = null;
            getRemainingSecondsMethod = null;
            untagPlayerMethod = null;
        }
    }

    /**
     * Checks if a player is currently in combat according to FoCombatTag.
     * Evaluates via FoCombatTag Java API (reflection) and PlaceholderAPI (%focombattag_tagged%).
     *
     * @param player the player to check
     * @return true if the player is combat-tagged, false otherwise
     */
    public static boolean isTagged(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        // 1. Direct API check via Reflection
        initReflection();
        if (isAvailableMethod != null && getMethod != null && isTaggedPlayerMethod != null) {
            try {
                Object available = isAvailableMethod.invoke(null);
                if (Boolean.TRUE.equals(available)) {
                    Object opt = getMethod.invoke(null);
                    if (opt instanceof Optional<?> optional && optional.isPresent()) {
                        Object apiInstance = optional.get();
                        Object result = isTaggedPlayerMethod.invoke(apiInstance, player);
                        if (result instanceof Boolean b && b) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. PlaceholderAPI check (%focombattag_tagged%)
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                String val = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%focombattag_tagged%");
                if ("true".equalsIgnoreCase(val)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    /**
     * Gets the remaining combat time in seconds.
     *
     * @param player the player to check
     * @return remaining combat seconds, or 0 if not tagged
     */
    public static long getRemainingSeconds(Player player) {
        if (player == null || !player.isOnline()) {
            return 0L;
        }

        // 1. Direct API check via Reflection
        initReflection();
        if (isAvailableMethod != null && getMethod != null && getRemainingSecondsMethod != null) {
            try {
                Object available = isAvailableMethod.invoke(null);
                if (Boolean.TRUE.equals(available)) {
                    Object opt = getMethod.invoke(null);
                    if (opt instanceof Optional<?> optional && optional.isPresent()) {
                        Object apiInstance = optional.get();
                        Object result = getRemainingSecondsMethod.invoke(apiInstance, player);
                        if (result instanceof Number n) {
                            return Math.max(0L, n.longValue());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. PlaceholderAPI check (%focombattag_remaining_raw%)
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                String val = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%focombattag_remaining_raw%");
                if (val != null && !val.isEmpty()) {
                    try {
                        return Math.max(0L, Long.parseLong(val.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return 0L;
    }

    /**
     * Removes the combat tag from the player via FoCombatTag API.
     * Operates completely silently in memory without console command dispatch.
     *
     * @param player the player to untag
     * @return true if successfully untagged, false otherwise
     */
    public static boolean untag(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        boolean success = false;
        initReflection();

        // 1. Try FoCombatTagApi.get().untag(player)
        if (isAvailableMethod != null && getMethod != null && untagPlayerMethod != null) {
            try {
                Object available = isAvailableMethod.invoke(null);
                if (Boolean.TRUE.equals(available)) {
                    Object opt = getMethod.invoke(null);
                    if (opt instanceof Optional<?> optional && optional.isPresent()) {
                        Object apiInstance = optional.get();
                        Object res = untagPlayerMethod.invoke(apiInstance, player);
                        if (res instanceof Boolean b && b) {
                            success = true;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. Direct CombatTagService reflection fallback (completely silent, in-memory)
        if (!success) {
            try {
                org.bukkit.plugin.Plugin foPlugin = Bukkit.getPluginManager().getPlugin("FoCombatTag");
                if (foPlugin == null) {
                    foPlugin = Bukkit.getPluginManager().getPlugin("focombattag");
                }
                if (foPlugin != null) {
                    Method combatTagsMethod = foPlugin.getClass().getMethod("combatTags");
                    combatTagsMethod.setAccessible(true);
                    Object service = combatTagsMethod.invoke(foPlugin);
                    if (service != null) {
                        try {
                            Method untagBoolMethod = service.getClass().getMethod("untag", Player.class, boolean.class);
                            untagBoolMethod.setAccessible(true);
                            Object res = untagBoolMethod.invoke(service, player, true);
                            if (res instanceof Boolean b && b) {
                                success = true;
                            }
                        } catch (NoSuchMethodException e) {
                            for (Method m : service.getClass().getMethods()) {
                                if (m.getName().equals("untag") && m.getParameterCount() == 3) {
                                    m.setAccessible(true);
                                    m.invoke(service, player, null, true);
                                    success = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return success;
    }
}
