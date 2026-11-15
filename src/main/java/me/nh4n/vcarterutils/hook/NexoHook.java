package me.nh4n.vcarterutils.hook;

import me.nh4n.vcarterutils.VCarterUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;

public final class NexoHook {

    private static Method itemFromIdMethod;
    private static boolean initialized = false;
    private static final NamespacedKey NEXO_ID_KEY = new NamespacedKey("nexo", "id");

    private NexoHook() {
    }

    public static void init(VCarterUtils plugin) {
        if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
            return;
        }
        try {
            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            itemFromIdMethod = nexoItemsClass.getMethod("itemFromId", String.class);
            initialized = true;

            // Listen for NexoItemsLoadedEvent (on server boot or /nexo reload)
            Class<? extends Event> eventClass = Class.forName("com.nexomc.nexo.api.events.NexoItemsLoadedEvent")
                    .asSubclass(Event.class);
            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    new Listener() {},
                    EventPriority.MONITOR,
                    (listener, event) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (plugin.getLobbyHotbarManager() != null) {
                            plugin.getLobbyHotbarManager().reload();
                        }
                        if (plugin.getSettingMenuManager() != null) {
                            plugin.getSettingMenuManager().reload();
                        }
                    }),
                    plugin
            );
            plugin.getLogger().info("Successfully hooked into Nexo!");
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to hook into Nexo API: " + t.getMessage());
        }
    }

    public static ItemStack getItemFromNexoId(String nexoId) {
        if (nexoId == null || nexoId.trim().isEmpty()) {
            return null;
        }
        String cleanId = nexoId.trim();
        if (cleanId.toLowerCase().startsWith("nexo:")) {
            cleanId = cleanId.substring(5).trim();
        }
        if (cleanId.isEmpty()) {
            return null;
        }

        try {
            if (!initialized && Bukkit.getPluginManager().getPlugin("Nexo") != null) {
                Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
                itemFromIdMethod = nexoItemsClass.getMethod("itemFromId", String.class);
                initialized = true;
            }
            if (itemFromIdMethod == null) {
                return null;
            }

            Object itemBuilder = itemFromIdMethod.invoke(null, cleanId);
            if (itemBuilder == null) {
                return null;
            }

            Method buildMethod = itemBuilder.getClass().getMethod("build");
            Object built = buildMethod.invoke(itemBuilder);
            if (built instanceof ItemStack stack) {
                ItemStack cloned = stack.clone();
                ItemMeta meta = cloned.getItemMeta();
                if (meta != null) {
                    // Remove nexo:id from clone PDC to prevent Nexo auto-resetting name/lore or placing furniture
                    meta.getPersistentDataContainer().remove(NEXO_ID_KEY);
                    cloned.setItemMeta(meta);
                }
                return cloned;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Creates an ItemStack from a ConfigurationSection:
     * 1. Prioritize "nexo-id" (e.g. nexo-id: "settings" or "cosmetic")
     * 2. Or "material" starting with "nexo:" prefix (e.g. material: "nexo:settings")
     * 3. Fallback to standard Bukkit Material if Nexo has not loaded the item.
     */
    public static ItemStack createItemFromSection(ConfigurationSection sec, Material fallbackMaterial) {
        if (sec != null) {
            String nexoId = sec.getString("nexo-id", "");
            String matRaw = sec.getString("material", "");

            if (nexoId != null && !nexoId.trim().isEmpty()) {
                ItemStack nexoStack = getItemFromNexoId(nexoId);
                if (nexoStack != null) {
                    return nexoStack;
                }
            }

            if (matRaw != null && matRaw.trim().toLowerCase().startsWith("nexo:")) {
                ItemStack nexoStack = getItemFromNexoId(matRaw.trim().substring(5));
                if (nexoStack != null) {
                    return nexoStack;
                }
            }

            if (matRaw != null && !matRaw.trim().isEmpty()) {
                Material mat = Material.matchMaterial(matRaw.trim());
                if (mat != null && mat != Material.AIR) {
                    return new ItemStack(mat, 1);
                }
            }
        }
        return new ItemStack(fallbackMaterial != null ? fallbackMaterial : Material.COMPASS, 1);
    }

    /**
     * Helper to create an ItemStack from either a Nexo ID (e.g. "nexo:next" or "next")
     * or fallback to a standard Bukkit Material.
     */
    public static ItemStack createItem(String nexoIdOrMat, Material fallbackMaterial) {
        if (nexoIdOrMat != null && !nexoIdOrMat.trim().isEmpty()) {
            String trimmed = nexoIdOrMat.trim();
            if (trimmed.toLowerCase().startsWith("nexo:")) {
                ItemStack nexoStack = getItemFromNexoId(trimmed.substring(5));
                if (nexoStack != null) {
                    return nexoStack;
                }
            } else {
                ItemStack nexoStack = getItemFromNexoId(trimmed);
                if (nexoStack != null) {
                    return nexoStack;
                }
            }
            Material mat = Material.matchMaterial(trimmed);
            if (mat != null && mat != Material.AIR) {
                return new ItemStack(mat, 1);
            }
        }
        return new ItemStack(fallbackMaterial != null ? fallbackMaterial : Material.STONE, 1);
    }
}
