package me.nh4n.vcarterutils.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreManager {

    // Luu tru ti so 1v1: Map<KillerUUID, Map<VictimUUID, KillCount>>
    private final Map<UUID, Map<UUID, Integer>> matchScores = new ConcurrentHashMap<>();

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
    }
}
