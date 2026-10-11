package me.nh4n.vcarterutils.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreManager {

    // Luu tru ti so 1v1: Map<KillerUUID, Map<VictimUUID, KillCount>>
    private final Map<UUID, Map<UUID, Integer>> matchScores = new ConcurrentHashMap<>();

    // Luu yeu cau reset: Map<TargetUUID, Map<SenderUUID, ExpirationTimestamp>>
    private final Map<UUID, Map<UUID, Long>> pendingResetRequests = new ConcurrentHashMap<>();

    public void recordKill(UUID killerId, UUID victimId) {
        matchScores.computeIfAbsent(killerId, k -> new ConcurrentHashMap<>())
                   .merge(victimId, 1, Integer::sum);
    }

    public int getKillsAgainst(UUID playerOne, UUID playerTwo) {
        Map<UUID, Integer> playerKills = matchScores.get(playerOne);
        if (playerKills == null) {
            return 0;
        }
        return playerKills.getOrDefault(playerTwo, 0);
    }

    public void resetScoresBetween(UUID playerOne, UUID playerTwo) {
        Map<UUID, Integer> p1Kills = matchScores.get(playerOne);
        if (p1Kills != null) {
            p1Kills.remove(playerTwo);
        }

        Map<UUID, Integer> p2Kills = matchScores.get(playerTwo);
        if (p2Kills != null) {
            p2Kills.remove(playerOne);
        }
    }

    public void clearAll() {
        matchScores.clear();
        pendingResetRequests.clear();
    }

    // --- Quan ly Reset Requests ---

    public void createRequest(UUID sender, UUID target, long timeoutSeconds) {
        long expireTime = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        pendingResetRequests.computeIfAbsent(target, k -> new ConcurrentHashMap<>())
                            .put(sender, expireTime);
    }

    public boolean hasValidRequest(UUID sender, UUID target) {
        Map<UUID, Long> requests = pendingResetRequests.get(target);
        if (requests == null) {
            return false;
        }
        Long expireTime = requests.get(sender);
        if (expireTime == null) {
            return false;
        }
        if (System.currentTimeMillis() > expireTime) {
            requests.remove(sender);
            return false;
        }
        return true;
    }

    public void removeRequest(UUID sender, UUID target) {
        Map<UUID, Long> requests = pendingResetRequests.get(target);
        if (requests != null) {
            requests.remove(sender);
            if (requests.isEmpty()) {
                pendingResetRequests.remove(target);
            }
        }
    }
}
