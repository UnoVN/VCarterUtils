package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BackManager {

    private final VCarterUtils plugin;
    // Luu tru vi tri tu tran gan nhat: Map<PlayerUUID, Location>
    private final Map<UUID, Location> deathLocations = new ConcurrentHashMap<>();
    private final Set<String> cachedBlacklistedWorlds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public BackManager(VCarterUtils plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cachedBlacklistedWorlds.clear();
        List<String> list = plugin.getConfig().getStringList("back.blacklisted-worlds");
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

    public void recordDeathLocation(UUID uuid, Location loc) {
        if (loc != null) {
            deathLocations.put(uuid, loc.clone());
        }
    }

    public Location getDeathLocation(UUID uuid) {
        return deathLocations.get(uuid);
    }

    public void clearDeathLocation(UUID uuid) {
        deathLocations.remove(uuid);
    }

    public void clearAll() {
        deathLocations.clear();
        cachedBlacklistedWorlds.clear();
    }
}
