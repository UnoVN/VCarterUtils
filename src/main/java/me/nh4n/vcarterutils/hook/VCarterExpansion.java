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

        // Format placeholder: %vcarterutils_score_<ten_doi_thu>%
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

        // %vcarterutils_pearl_cooldown% -> Cooldown thuc te dang ap dung tai world hien tai (giay)
        if (params.equalsIgnoreCase("pearl_cooldown")) {
            return String.valueOf(plugin.getPearlManager().getEffectiveCooldownSeconds(player));
        }

        // %vcarterutils_pearl_cooldown_personal% -> Cooldown ca nhan da chon trong /setting (giay)
        if (params.equalsIgnoreCase("pearl_cooldown_personal")) {
            return String.valueOf(plugin.getPearlManager().getPlayerPersonalSeconds(player.getUniqueId()));
        }

        // %vcarterutils_pearl_cooldown_remaining% -> Thoi gian hoi chieu pearl con lai (vd: 2.5)
        if (params.equalsIgnoreCase("pearl_cooldown_remaining")) {
            int ticks = player.getCooldown(Material.ENDER_PEARL);
            if (ticks <= 0) {
                return "0";
            }
            return String.format(Locale.US, "%.1f", ticks / 20.0);
        }

        return null;
    }
}
