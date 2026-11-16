package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BackManager {

    private final VCarterUtils plugin;
    // Stores the most recent death location: Map<PlayerUUID, Location>
    private final Map<UUID, Location> deathLocations = new ConcurrentHashMap<>();
    private final Set<String> cachedBlacklistedWorlds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // Tracks players currently undergoing /back teleport to prevent other plugins from cancelling PlayerTeleportEvent
    private final Set<UUID> pendingBackTeleports = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // Tracks players who have already consumed /back for their current life
    private final Set<UUID> usedBackForCurrentLife = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // Tracks consumed death locations to prevent Paper NBT fallback from reviving them
    private final Map<UUID, Location> consumedDeathLocations = new ConcurrentHashMap<>();

    public BackManager(VCarterUtils plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cachedBlacklistedWorlds.clear();
        List<String> list = plugin.getConfig().getStringList("back.blacklisted-worlds");
        // Automatically migrate if old config contained the placeholder world "ffa_diamond"
        if (list.size() == 1 && "ffa_diamond".equalsIgnoreCase(list.get(0).trim())) {
            plugin.getConfig().set("back.blacklisted-worlds", Collections.emptyList());
            plugin.saveFeatureConfig("back");
            list = Collections.emptyList();
        }
        for (String w : list) {
            if (w != null && !w.trim().isEmpty()) {
                cachedBlacklistedWorlds.add(w.trim().toLowerCase());
            }
        }
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("back.enabled", true);
    }

    public boolean isWorldBlacklisted(World world) {
        if (world == null) return false;
        return cachedBlacklistedWorlds.contains(world.getName().trim().toLowerCase());
    }

    public boolean isWorldBlacklisted(String worldName) {
        if (worldName == null) return false;
        return cachedBlacklistedWorlds.contains(worldName.trim().toLowerCase());
    }

    /**
     * Records a brand new death location when a player dies in PlayerDeathEvent.
     * Clears any consumed markers so this new death can be used for /back.
     */
    public void recordDeathLocation(UUID uuid, Location loc) {
        if (uuid == null || loc == null || loc.getWorld() == null) {
            return;
        }
        deathLocations.put(uuid, loc.clone());
        usedBackForCurrentLife.remove(uuid);
        consumedDeathLocations.remove(uuid);
    }

    /**
     * Gets the valid, unconsumed death location for a player.
     * Returns null if player hasn't died, or if they already used /back for their last death.
     */
    public Location getValidDeathLocation(Player player) {
        if (player == null) return null;
        UUID uuid = player.getUniqueId();

        // 1. If already consumed /back for current life, strictly deny!
        if (usedBackForCurrentLife.contains(uuid)) {
            return null;
        }

        // 2. Check active recorded death location
        Location loc = deathLocations.get(uuid);
        if (loc != null && loc.getWorld() != null) {
            return loc;
        }

        // 3. Fallback: Check Paper entity getLastDeathLocation() only if not consumed
        Location paperDeath = player.getLastDeathLocation();
        if (paperDeath == null || paperDeath.getWorld() == null) {
            return null;
        }

        Location consumed = consumedDeathLocations.get(uuid);
        if (consumed != null && isSameLocation(paperDeath, consumed)) {
            return null;
        }

        // Valid fallback: record it into active death locations and return
        deathLocations.put(uuid, paperDeath.clone());
        return paperDeath;
    }

    public Location getDeathLocation(UUID uuid) {
        if (usedBackForCurrentLife.contains(uuid)) {
            return null;
        }
        return deathLocations.get(uuid);
    }

    /**
     * Consumes the death location once /back teleport has succeeded.
     * Ensures the player cannot use /back again until they die next time.
     */
    public void consumeDeathLocation(UUID uuid) {
        if (uuid == null) return;
        usedBackForCurrentLife.add(uuid);
        Location loc = deathLocations.remove(uuid);
        if (loc != null && loc.getWorld() != null) {
            consumedDeathLocations.put(uuid, loc.clone());
        } else {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Location lastDeath = player.getLastDeathLocation();
                if (lastDeath != null && lastDeath.getWorld() != null) {
                    consumedDeathLocations.put(uuid, lastDeath.clone());
                }
            }
        }
    }

    public void clearDeathLocation(UUID uuid) {
        consumeDeathLocation(uuid);
    }

    private boolean isSameLocation(Location l1, Location l2) {
        if (l1 == null || l2 == null) return false;
        if (l1.getWorld() == null || l2.getWorld() == null) return false;
        if (!l1.getWorld().getName().equalsIgnoreCase(l2.getWorld().getName())) return false;
        return Math.abs(l1.getBlockX() - l2.getBlockX()) <= 2
                && Math.abs(l1.getBlockY() - l2.getBlockY()) <= 2
                && Math.abs(l1.getBlockZ() - l2.getBlockZ()) <= 2;
    }

    public boolean isPendingBackTeleport(UUID uuid) {
        if (uuid == null) return false;
        return pendingBackTeleports.contains(uuid);
    }

    public void setPendingBackTeleport(UUID uuid, boolean pending) {
        if (uuid == null) return;
        if (pending) {
            pendingBackTeleports.add(uuid);
        } else {
            pendingBackTeleports.remove(uuid);
        }
    }

    public void clearAll() {
        deathLocations.clear();
        usedBackForCurrentLife.clear();
        consumedDeathLocations.clear();
        cachedBlacklistedWorlds.clear();
        pendingBackTeleports.clear();
    }
}
