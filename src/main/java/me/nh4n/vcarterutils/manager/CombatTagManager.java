package me.nh4n.vcarterutils.manager;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.hook.FoCombatTagHook;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CombatTagManager {

    private final VCarterUtils plugin;

    private final List<Integer> availableOptions = new ArrayList<>();
    private final Map<String, Integer> forcedWorldCombatTimers = new ConcurrentHashMap<>();
    private final List<String> globalBlockedCommands = new ArrayList<>();
    private final Map<String, List<String>> worldBlockedCommands = new ConcurrentHashMap<>();

    private boolean globalForceEnabled = false;
    private int globalForceSeconds = 15;

    // Anti-Interrupt 1v1 Protection
    private boolean antiInterruptGlobal = false;
    private final Map<String, Boolean> worldAntiInterrupt = new ConcurrentHashMap<>();

    // Active Duel Pairs: Player UUID -> Opponent Player UUID
    private final Map<UUID, UUID> combatOpponents = new ConcurrentHashMap<>();
    // Player UUID -> Timestamp of last combat interaction (ms)
    private final Map<UUID, Long> combatTimestamps = new ConcurrentHashMap<>();

    public CombatTagManager(VCarterUtils plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        int def = getDefaultSeconds();

        availableOptions.clear();
        List<Integer> rawOptions = plugin.getConfig().getIntegerList("combat-tag.available-options");
        if (rawOptions.isEmpty()) {
            rawOptions = List.of(5, 10, 15, 20, 25, 30, 45, 60);
        }

        for (int val : rawOptions) {
            if (val > 0 && !availableOptions.contains(val)) {
                availableOptions.add(val);
            }
        }
        if (!availableOptions.contains(def)) {
            availableOptions.add(def);
        }
        Collections.sort(availableOptions);

        this.globalForceEnabled = plugin.getConfig().getBoolean("combat-tag.global-force.enabled", false);
        this.globalForceSeconds = Math.max(1, plugin.getConfig().getInt("combat-tag.global-force.seconds", def));

        forcedWorldCombatTimers.clear();
        ConfigurationSection worldSection = plugin.getConfig().getConfigurationSection("combat-tag.force-worlds.worlds");
        if (worldSection != null) {
            for (String worldName : worldSection.getKeys(false)) {
                int sec = worldSection.getInt(worldName, -1);
                // 0 means completely disable combat tag in this world; >0 means custom timer
                if (sec >= 0) {
                    forcedWorldCombatTimers.put(worldName.trim().toLowerCase(), sec);
                }
            }
        }

        // Anti-Interrupt configuration
        this.antiInterruptGlobal = plugin.getConfig().getBoolean("combat-tag.anti-interrupt.enabled", false);
        worldAntiInterrupt.clear();
        ConfigurationSection antiWorldsSec = plugin.getConfig().getConfigurationSection("combat-tag.anti-interrupt.worlds");
        if (antiWorldsSec != null) {
            for (String worldName : antiWorldsSec.getKeys(false)) {
                if (antiWorldsSec.isBoolean(worldName)) {
                    worldAntiInterrupt.put(worldName.trim().toLowerCase(), antiWorldsSec.getBoolean(worldName));
                }
            }
        }

        // Load blocked commands
        globalBlockedCommands.clear();
        List<String> rawGlobalCmds = plugin.getConfig().getStringList("combat-tag.blocked-commands.global");
        if (rawGlobalCmds.isEmpty()) {
            rawGlobalCmds = List.of("spawn", "tpa", "tpaccept", "tpahere", "home", "warp", "kit", "regear", "lobby");
        }
        for (String cmd : rawGlobalCmds) {
            if (cmd != null && !cmd.trim().isEmpty()) {
                String cleaned = cleanCommand(cmd);
                if (!cleaned.equals("back") && !cleaned.equals("deathback") && !cleaned.equals("return")) {
                    globalBlockedCommands.add(cleaned);
                }
            }
        }

        worldBlockedCommands.clear();
        ConfigurationSection worldCmdSection = plugin.getConfig().getConfigurationSection("combat-tag.blocked-commands.worlds");
        if (worldCmdSection != null) {
            for (String worldName : worldCmdSection.getKeys(false)) {
                List<String> list = worldCmdSection.getStringList(worldName);
                List<String> cleanList = new ArrayList<>();
                for (String cmd : list) {
                    if (cmd != null && !cmd.trim().isEmpty()) {
                        cleanList.add(cleanCommand(cmd));
                    }
                }
                worldBlockedCommands.put(worldName.trim().toLowerCase(), cleanList);
            }
        }
    }

    private String cleanCommand(String cmd) {
        String s = cmd.trim().toLowerCase();
        if (s.startsWith("/")) {
            s = s.substring(1).trim();
        }
        return s;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("combat-tag.enabled", true);
    }

    public boolean isForceWorldsEnabled() {
        return plugin.getConfig().getBoolean("combat-tag.force-worlds.enabled", true);
    }

    public boolean isGlobalForceEnabled() {
        return globalForceEnabled;
    }

    public void setGlobalForceEnabled(boolean enabled) {
        this.globalForceEnabled = enabled;
        plugin.getConfig().set("combat-tag.global-force.enabled", enabled);
        plugin.saveFeatureConfig("combattag");
    }

    public int getGlobalForceSeconds() {
        return globalForceSeconds;
    }

    public void setGlobalForceSeconds(int seconds) {
        this.globalForceSeconds = Math.max(1, seconds);
        plugin.getConfig().set("combat-tag.global-force.seconds", this.globalForceSeconds);
        plugin.saveFeatureConfig("combattag");
    }

    public int cycleGlobalForceSeconds(boolean forward) {
        if (availableOptions.isEmpty()) {
            return globalForceSeconds;
        }
        int idx = availableOptions.indexOf(globalForceSeconds);
        int nextIdx;
        if (forward) {
            nextIdx = (idx == -1 || idx + 1 >= availableOptions.size()) ? 0 : idx + 1;
        } else {
            nextIdx = (idx <= 0) ? availableOptions.size() - 1 : idx - 1;
        }
        setGlobalForceSeconds(availableOptions.get(nextIdx));
        return globalForceSeconds;
    }

    public int getDefaultSeconds() {
        return Math.max(1, plugin.getConfig().getInt("combat-tag.default-seconds", 15));
    }

    public void setDefaultSeconds(int seconds) {
        int clean = Math.max(1, seconds);
        plugin.getConfig().set("combat-tag.default-seconds", clean);
        plugin.saveFeatureConfig("combattag");
        reload();
    }

    public List<Integer> getAvailableOptions() {
        return Collections.unmodifiableList(availableOptions);
    }

    public Set<String> getConfiguredForcedWorldNames() {
        return new HashSet<>(forcedWorldCombatTimers.keySet());
    }

    public boolean isWorldForced(String worldName) {
        if (worldName == null || !isForceWorldsEnabled()) {
            return false;
        }
        return forcedWorldCombatTimers.containsKey(worldName.trim().toLowerCase());
    }

    public int getForcedWorldSeconds(String worldName) {
        if (!isWorldForced(worldName)) {
            return -1;
        }
        return forcedWorldCombatTimers.getOrDefault(worldName.trim().toLowerCase(), -1);
    }

    public void setWorldForceSeconds(String worldName, int seconds) {
        if (worldName == null || worldName.trim().isEmpty()) {
            return;
        }
        String cleanKey = worldName.trim();
        String lowerKey = cleanKey.toLowerCase();

        for (String k : new HashSet<>(getConfiguredWorldKeys())) {
            if (k.equalsIgnoreCase(lowerKey)) {
                plugin.getConfig().set("combat-tag.force-worlds.worlds." + k, null);
            }
        }

        if (seconds < 0) {
            forcedWorldCombatTimers.remove(lowerKey);
            plugin.getConfig().set("combat-tag.force-worlds.worlds." + cleanKey, null);
        } else {
            forcedWorldCombatTimers.put(lowerKey, seconds);
            plugin.getConfig().set("combat-tag.force-worlds.enabled", true);
            plugin.getConfig().set("combat-tag.force-worlds.worlds." + cleanKey, seconds);
        }
        plugin.saveFeatureConfig("combattag");
    }

    private Set<String> getConfiguredWorldKeys() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("combat-tag.force-worlds.worlds");
        return sec != null ? sec.getKeys(false) : Collections.emptySet();
    }

    /**
     * Cycles through available combat tag timers for a world:
     * -1 (OFF) -> 0 (DISABLED) -> [availableOptions: 5s, 10s, ...] -> -1 (OFF)
     */
    public int cycleWorldForceSeconds(String worldName) {
        return cycleWorldForceSeconds(worldName, true);
    }

    public int cycleWorldForceSeconds(String worldName, boolean forward) {
        int current = getForcedWorldSeconds(worldName);
        if (availableOptions.isEmpty()) {
            int next = (current < 0) ? 0 : -1;
            setWorldForceSeconds(worldName, next);
            return next;
        }

        if (forward) {
            if (current < 0) {
                // OFF -> 0 (DISABLED)
                setWorldForceSeconds(worldName, 0);
                return 0;
            } else if (current == 0) {
                // 0 -> First option (e.g. 5s)
                int first = availableOptions.get(0);
                setWorldForceSeconds(worldName, first);
                return first;
            }

            int idx = availableOptions.indexOf(current);
            if (idx == -1 || idx + 1 >= availableOptions.size()) {
                // Last option -> OFF (-1)
                setWorldForceSeconds(worldName, -1);
                return -1;
            }

            int next = availableOptions.get(idx + 1);
            setWorldForceSeconds(worldName, next);
            return next;
        } else {
            if (current < 0) {
                // OFF -> Last option
                int last = availableOptions.get(availableOptions.size() - 1);
                setWorldForceSeconds(worldName, last);
                return last;
            } else if (current == 0) {
                // 0 -> OFF (-1)
                setWorldForceSeconds(worldName, -1);
                return -1;
            }

            int idx = availableOptions.indexOf(current);
            if (idx <= 0) {
                // First option -> 0 (DISABLED)
                setWorldForceSeconds(worldName, 0);
                return 0;
            }

            int prev = availableOptions.get(idx - 1);
            setWorldForceSeconds(worldName, prev);
            return prev;
        }
    }

    /**
     * Calculates the effective combat tag timer in seconds for a player in a given world:
     * - 0: Combat tag is disabled in this world
     * - > 0: Specific custom combat tag duration
     * - -1: Not forced, standard FoCombatTag config applies
     */
    public int getEffectiveCombatSeconds(World world) {
        if (!isEnabled()) {
            return -1;
        }
        if (world != null && isForceWorldsEnabled()) {
            String lower = world.getName().trim().toLowerCase();
            if (forcedWorldCombatTimers.containsKey(lower)) {
                return forcedWorldCombatTimers.get(lower);
            }
        }
        if (isGlobalForceEnabled()) {
            return globalForceSeconds;
        }
        return -1;
    }

    /**
     * Checks if a command is blocked during combat in the given world.
     */
    public boolean isCommandBlocked(World world, String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return false;
        }
        String clean = rawMessage.trim();
        if (clean.startsWith("/")) {
            clean = clean.substring(1).trim();
        }
        String[] tokens = clean.split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            return false;
        }

        String root = tokens[0].toLowerCase();
        if (root.contains(":")) {
            root = root.substring(root.indexOf(':') + 1);
        }
        if (root.equals("back") || root.equals("deathback") || root.equals("return")) {
            return false;
        }
        String fullCommand = clean.toLowerCase();

        // 1. Check global blocked commands
        for (String blocked : globalBlockedCommands) {
            if (matchesCommand(root, fullCommand, blocked)) {
                return true;
            }
        }

        // 2. Check world-specific blocked commands
        if (world != null) {
            List<String> worldCmds = worldBlockedCommands.get(world.getName().trim().toLowerCase());
            if (worldCmds != null) {
                for (String blocked : worldCmds) {
                    if (matchesCommand(root, fullCommand, blocked)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean matchesCommand(String root, String fullCommand, String blocked) {
        if (blocked.equalsIgnoreCase(root) || blocked.equalsIgnoreCase(fullCommand)) {
            return true;
        }
        if (blocked.contains(" ") && fullCommand.startsWith(blocked.toLowerCase() + " ")) {
            return true;
        }
        return false;
    }

    public List<String> getGlobalBlockedCommands() {
        return Collections.unmodifiableList(globalBlockedCommands);
    }

    public List<String> getWorldBlockedCommands(String worldName) {
        if (worldName == null) return Collections.emptyList();
        List<String> list = worldBlockedCommands.get(worldName.trim().toLowerCase());
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    public void addBlockedCommand(String worldOrGlobal, String cmd) {
        String clean = cleanCommand(cmd);
        if (clean.isEmpty()) return;

        if (worldOrGlobal == null || worldOrGlobal.equalsIgnoreCase("global")) {
            if (!globalBlockedCommands.contains(clean)) {
                globalBlockedCommands.add(clean);
                plugin.getConfig().set("combat-tag.blocked-commands.global", new ArrayList<>(globalBlockedCommands));
                plugin.saveFeatureConfig("combattag");
            }
        } else {
            String cleanWorld = worldOrGlobal.trim();
            String lower = cleanWorld.toLowerCase();
            List<String> list = worldBlockedCommands.computeIfAbsent(lower, k -> new ArrayList<>());
            if (!list.contains(clean)) {
                list.add(clean);
                clearCaseVariantWorldKey("combat-tag.blocked-commands.worlds", cleanWorld);
                plugin.getConfig().set("combat-tag.blocked-commands.worlds." + cleanWorld, new ArrayList<>(list));
                plugin.saveFeatureConfig("combattag");
            }
        }
    }

    public boolean removeBlockedCommand(String worldOrGlobal, String cmd) {
        String clean = cleanCommand(cmd);
        if (clean.isEmpty()) return false;

        if (worldOrGlobal == null || worldOrGlobal.equalsIgnoreCase("global")) {
            boolean removed = globalBlockedCommands.remove(clean);
            if (removed) {
                plugin.getConfig().set("combat-tag.blocked-commands.global", new ArrayList<>(globalBlockedCommands));
                plugin.saveFeatureConfig("combattag");
            }
            return removed;
        } else {
            String cleanWorld = worldOrGlobal.trim();
            String lower = cleanWorld.toLowerCase();
            List<String> list = worldBlockedCommands.get(lower);
            if (list != null) {
                boolean removed = list.remove(clean);
                if (removed) {
                    clearCaseVariantWorldKey("combat-tag.blocked-commands.worlds", cleanWorld);
                    if (list.isEmpty()) {
                        worldBlockedCommands.remove(lower);
                    } else {
                        plugin.getConfig().set("combat-tag.blocked-commands.worlds." + cleanWorld, new ArrayList<>(list));
                    }
                    plugin.saveFeatureConfig("combattag");
                }
                return removed;
            }
        }
        return false;
    }

    private void clearCaseVariantWorldKey(String sectionPath, String worldName) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(sectionPath);
        if (sec == null) return;
        for (String existing : new HashSet<>(sec.getKeys(false))) {
            if (existing.equalsIgnoreCase(worldName)) {
                sec.set(existing, null);
            }
        }
    }

    // ==========================================
    //       ANTI-INTERRUPT 1V1 PROTECTION
    // ==========================================

    public boolean isAntiInterruptGlobal() {
        return antiInterruptGlobal;
    }

    public void setAntiInterruptGlobal(boolean enabled) {
        this.antiInterruptGlobal = enabled;
        plugin.getConfig().set("combat-tag.anti-interrupt.enabled", enabled);
        plugin.saveFeatureConfig("combattag");
    }

    public void toggleAntiInterruptGlobal() {
        setAntiInterruptGlobal(!antiInterruptGlobal);
    }

    public Boolean getWorldAntiInterrupt(String worldName) {
        if (worldName == null) return null;
        return worldAntiInterrupt.get(worldName.trim().toLowerCase());
    }

    public boolean isAntiInterruptEnabled(World world) {
        if (world == null) return antiInterruptGlobal;
        return isAntiInterruptEnabled(world.getName());
    }

    public boolean isAntiInterruptEnabled(String worldName) {
        if (worldName == null) return antiInterruptGlobal;
        Boolean val = worldAntiInterrupt.get(worldName.trim().toLowerCase());
        return val != null ? val : antiInterruptGlobal;
    }

    public void setWorldAntiInterrupt(String worldName, Boolean enabled) {
        if (worldName == null || worldName.trim().isEmpty()) return;
        String cleanKey = worldName.trim();
        String lowerKey = cleanKey.toLowerCase();

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("combat-tag.anti-interrupt.worlds");
        if (sec != null) {
            for (String k : new HashSet<>(sec.getKeys(false))) {
                if (k.equalsIgnoreCase(lowerKey)) {
                    plugin.getConfig().set("combat-tag.anti-interrupt.worlds." + k, null);
                }
            }
        }

        if (enabled == null) {
            worldAntiInterrupt.remove(lowerKey);
            plugin.getConfig().set("combat-tag.anti-interrupt.worlds." + cleanKey, null);
        } else {
            worldAntiInterrupt.put(lowerKey, enabled);
            plugin.getConfig().set("combat-tag.anti-interrupt.worlds." + cleanKey, enabled);
        }
        plugin.saveFeatureConfig("combattag");
    }

    public Boolean toggleWorldAntiInterrupt(String worldName) {
        if (worldName == null) return null;
        Boolean current = getWorldAntiInterrupt(worldName);
        Boolean next;
        if (current == null) {
            // Default -> Opposite of global default
            next = !isAntiInterruptGlobal();
        } else if (current) {
            // ON -> OFF
            next = false;
        } else {
            // OFF -> DEFAULT (null)
            next = null;
        }
        setWorldAntiInterrupt(worldName, next);
        return next;
    }

    // ==========================================
    //       ACTIVE COMBAT DUEL TRACKING
    // ==========================================

    /**
     * Gets the active combat opponent of a player, if still valid and not expired.
     * Returns null if player is not currently locked in combat with anyone.
     */
    public UUID getActiveOpponent(Player player) {
        if (player == null || !player.isOnline()) return null;
        UUID uuid = player.getUniqueId();
        UUID opponentUuid = combatOpponents.get(uuid);
        if (opponentUuid == null) return null;

        Player opponent = Bukkit.getPlayer(opponentUuid);
        if (opponent == null || !opponent.isOnline() || !opponent.getWorld().equals(player.getWorld())) {
            endCombat(uuid);
            return null;
        }

        if (player.isDead() || opponent.isDead()) {
            endCombat(uuid);
            return null;
        }

        Long lastCombat = combatTimestamps.get(uuid);
        if (lastCombat == null) {
            endCombat(uuid);
            return null;
        }

        long effectiveSec = getEffectiveCombatSeconds(player.getWorld());
        if (effectiveSec <= 0) {
            effectiveSec = getDefaultSeconds();
        }
        long timeoutMs = effectiveSec * 1000L;

        long elapsed = System.currentTimeMillis() - lastCombat;
        boolean tagged = FoCombatTagHook.isTagged(player) || FoCombatTagHook.isTagged(opponent);

        if (elapsed > timeoutMs && !tagged) {
            endCombat(uuid);
            return null;
        }

        return opponentUuid;
    }

    public boolean isInCombatWith(Player p1, Player p2) {
        if (p1 == null || p2 == null) return false;
        UUID opp1 = getActiveOpponent(p1);
        return opp1 != null && opp1.equals(p2.getUniqueId());
    }

    public void recordCombat(Player p1, Player p2) {
        if (p1 == null || p2 == null || p1.equals(p2)) return;
        long now = System.currentTimeMillis();
        combatOpponents.put(p1.getUniqueId(), p2.getUniqueId());
        combatOpponents.put(p2.getUniqueId(), p1.getUniqueId());
        combatTimestamps.put(p1.getUniqueId(), now);
        combatTimestamps.put(p2.getUniqueId(), now);
    }

    public void endCombat(UUID playerUuid) {
        if (playerUuid == null) return;
        UUID opponentUuid = combatOpponents.remove(playerUuid);
        combatTimestamps.remove(playerUuid);
        if (opponentUuid != null) {
            combatOpponents.remove(opponentUuid);
            combatTimestamps.remove(opponentUuid);
        }
    }

    public void clearAll() {
        combatOpponents.clear();
        combatTimestamps.clear();
    }
}

