package me.nh4n.vcarterutils.util;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;

public final class MessageUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MessageUtil() {}

    /**
     * Formats raw message by replacing theme prefixes and Catppuccin color placeholders.
     */
    public static String format(VCarterUtils plugin, String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        FileConfiguration cfg = plugin.getConfig();

        // 1. Prefixes (warning_blue, warning_red, warning_purple)
        String pfxBlue = cfg.getString("theme.prefixes.blue", "<glyph:warning_blue> ");
        String pfxRed = cfg.getString("theme.prefixes.red", "<glyph:warning_red> ");
        String pfxPurple = cfg.getString("theme.prefixes.purple", "<glyph:warning_purple> ");

        String pfxInfo = cfg.getString("theme.prefixes.info", pfxBlue);
        String pfxSuccess = cfg.getString("theme.prefixes.success", pfxBlue);
        String pfxError = cfg.getString("theme.prefixes.error", pfxRed);
        String pfxCombat = cfg.getString("theme.prefixes.combat", pfxRed);
        String pfxWarning = cfg.getString("theme.prefixes.warning", pfxPurple);
        String pfxAdmin = cfg.getString("theme.prefixes.admin", pfxPurple);

        // 2. Theme Colors (Catppuccin Pastel Palette)
        String cPrimary = cfg.getString("theme.colors.primary", "<#89dceb>");
        String cSuccess = cfg.getString("theme.colors.success", "<#a6e3a1>");
        String cError = cfg.getString("theme.colors.error", "<#f38ba8>");
        String cWarning = cfg.getString("theme.colors.warning", "<#f9e2af>");
        String cPurple = cfg.getString("theme.colors.purple", "<#cba6f7>");
        String cText = cfg.getString("theme.colors.text", "<#a6adc8>");
        String cMuted = cfg.getString("theme.colors.muted", "<#6c7086>");

        return message
                .replace("%prefix_error%", pfxError)
                .replace("%prefix_warning%", pfxWarning)
                .replace("%prefix_success%", pfxSuccess)
                .replace("%prefix_admin%", pfxAdmin)
                .replace("%prefix_combat%", pfxCombat)
                .replace("%prefix_info%", pfxInfo)
                .replace("%prefix_blue%", pfxBlue)
                .replace("%prefix_red%", pfxRed)
                .replace("%prefix_purple%", pfxPurple)
                .replace("%prefix%", pfxInfo)
                .replace("%c_primary%", cPrimary)
                .replace("%c_success%", cSuccess)
                .replace("%c_error%", cError)
                .replace("%c_warning%", cWarning)
                .replace("%c_purple%", cPurple)
                .replace("%c_text%", cText)
                .replace("%c_muted%", cMuted)
                .replace("%color_primary%", cPrimary)
                .replace("%color_success%", cSuccess)
                .replace("%color_error%", cError)
                .replace("%color_warning%", cWarning)
                .replace("%color_purple%", cPurple)
                .replace("%color_text%", cText)
                .replace("%color_muted%", cMuted);
    }

    /**
     * Replaces placeholders and parses string into an Adventure Component.
     */
    public static Component parse(VCarterUtils plugin, String message) {
        return MM.deserialize(format(plugin, message));
    }
}
