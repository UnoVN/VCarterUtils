package me.nh4n.vcarterutils.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.nh4n.vcarterutils.VCarterUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class VCarterExpansion extends PlaceholderExpansion {

    private final VCarterUtils plugin;

    public VCarterExpansion(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "vcarterutils";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Nh4n";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        if (params.startsWith("score_")) {
            String opponentName = params.substring(6);
            Player opponent = null;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(opponentName)) {
                    opponent = p;
                    break;
                }
            }
            if (opponent == null) {
                return "0";
            }
            int kills = plugin.getScoreManager().getKillsAgainst(player.getUniqueId(), opponent.getUniqueId());
            return String.valueOf(kills);
        }

        if (params.equalsIgnoreCase("pearl_cooldown")) {
            return String.valueOf(plugin.getPearlManager().getEffectiveCooldownSeconds(player));
        }

        if (params.equalsIgnoreCase("pearl_cooldown_personal")) {
            return String.valueOf(plugin.getPearlManager().getPlayerPersonalSeconds(player.getUniqueId()));
        }

        if (params.equalsIgnoreCase("pearl_cooldown_remaining")) {
            int ticks = player.getCooldown(Material.ENDER_PEARL);
            if (ticks <= 0) {
                return "0";
            }
            return String.format(Locale.US, "%.1f", ticks / 20.0);
        }

        if (params.equalsIgnoreCase("mace_cooldown")) {
            return String.valueOf(plugin.getMaceManager().getEffectiveCooldownSeconds(player));
        }

        if (params.equalsIgnoreCase("mace_cooldown_personal")) {
            return String.valueOf(plugin.getMaceManager().getPlayerPersonalSeconds(player.getUniqueId()));
        }

        if (params.equalsIgnoreCase("mace_cooldown_remaining")) {
            int ticks = player.getCooldown(Material.MACE);
            if (ticks <= 0) {
                return "0";
            }
            return String.format(Locale.US, "%.1f", ticks / 20.0);
        }

        if (params.equalsIgnoreCase("xbow_last_ticks")) {
            int last = plugin.getXbowManager().getLastTicks(player.getUniqueId());
            return last > 0 ? String.valueOf(last) : "-";
        }

        if (params.equalsIgnoreCase("xbow_best_ticks")) {
            int best = plugin.getXbowManager().getBestTicks(player.getUniqueId());
            return best > 0 ? String.valueOf(best) : "-";
        }

        return null;
    }
}
