package me.nh4n.vcarterutils.command;

import me.nh4n.vcarterutils.VCarterUtils;
import me.nh4n.vcarterutils.hook.FoCombatTagHook;
import me.nh4n.vcarterutils.manager.TrainingManager;
import me.nh4n.vcarterutils.manager.TrainingOption;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TrainingCommand implements CommandExecutor, TabCompleter {

    private final VCarterUtils plugin;

    public TrainingCommand(VCarterUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only."));
            return true;
        }

        TrainingManager trainingManager = plugin.getTrainingManager();

        // /training with no args opens the Training Menu directly
        if (args.length == 0 || args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("menu")) {
            if (plugin.getConfig().getBoolean("training.disable-in-combat", true) && FoCombatTagHook.isTagged(player)) {
                String combatMsg = plugin.getConfig().getString(
                        "training.messages.cannot-in-combat",
                        "%prefix_combat%%c_error%<b>ɪɴ ᴄᴏᴍʙᴀᴛ</b> %c_muted%» %c_text%You cannot use Training Mode during combat."
                );
                player.sendMessage(plugin.parseMessage(combatMsg));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return true;
            }
            trainingManager.openTrainingMenu(player);
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("toggle")) {
            if (plugin.getConfig().getBoolean("training.disable-in-combat", true) && FoCombatTagHook.isTagged(player)) {
                String combatMsg = plugin.getConfig().getString(
                        "training.messages.cannot-in-combat",
                        "%prefix_combat%%c_error%<b>ɪɴ ᴄᴏᴍʙᴀᴛ</b> %c_muted%» %c_text%You cannot use Training Mode during combat."
                );
                player.sendMessage(plugin.parseMessage(combatMsg));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return true;
            }

            if (trainingManager.isWorldBlacklisted(player.getWorld().getName())) {
                String blMsg = plugin.getConfig().getString(
                        "training.messages.blacklisted-world",
                        "%prefix_warning%%c_error%<b>ʀᴇsᴛʀɪᴄᴛᴇᴅ</b> %c_muted%» %c_text%Training Mode is disabled in this world."
                );
                player.sendMessage(plugin.parseMessage(blMsg));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return true;
            }

            boolean enabled = trainingManager.toggleMaster(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, enabled ? 1.5f : 0.8f);

            String statusStr = enabled
                    ? plugin.getConfig().getString("training.messages.status-enabled", "%c_success%<b>✔ ᴇɴᴀʙʟᴇᴅ</b>")
                    : plugin.getConfig().getString("training.messages.status-disabled", "%c_error%<b>✖ ᴅɪsᴀʙʟᴇᴅ</b>");
            String raw = plugin.getConfig().getString(
                    "training.messages.toggled",
                    "%prefix_info%%c_text%Training Mode %c_muted%» %status%"
            );
            player.sendMessage(plugin.parseMessage(raw.replace("%status%", statusStr)));
            return true;
        }

        if (sub.equals("on") || sub.equals("enable")) {
            if (plugin.getConfig().getBoolean("training.disable-in-combat", true) && FoCombatTagHook.isTagged(player)) {
                String combatMsg = plugin.getConfig().getString(
                        "training.messages.cannot-in-combat",
                        "%prefix_combat%%c_error%<b>ɪɴ ᴄᴏᴍʙᴀᴛ</b> %c_muted%» %c_text%You cannot use Training Mode during combat."
                );
                player.sendMessage(plugin.parseMessage(combatMsg));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return true;
            }
            if (trainingManager.isWorldBlacklisted(player.getWorld().getName())) {
                String blMsg = plugin.getConfig().getString(
                        "training.messages.blacklisted-world",
                        "%prefix_warning%%c_error%<b>ʀᴇsᴛʀɪᴄᴛᴇᴅ</b> %c_muted%» %c_text%Training Mode is disabled in this world."
                );
                player.sendMessage(plugin.parseMessage(blMsg));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return true;
            }
            trainingManager.setMasterEnabled(player.getUniqueId(), true);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            String statusStr = plugin.getConfig().getString("training.messages.status-enabled", "%c_success%<b>✔ ᴇɴᴀʙʟᴇᴅ</b>");
            String raw = plugin.getConfig().getString("training.messages.toggled", "%prefix_info%%c_text%Training Mode %c_muted%» %status%");
            player.sendMessage(plugin.parseMessage(raw.replace("%status%", statusStr)));
            return true;
        }

        if (sub.equals("off") || sub.equals("disable")) {
            trainingManager.setMasterEnabled(player.getUniqueId(), false);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.8f);
            String statusStr = plugin.getConfig().getString("training.messages.status-disabled", "%c_error%<b>✖ ᴅɪsᴀʙʟᴇᴅ</b>");
            String raw = plugin.getConfig().getString("training.messages.toggled", "%prefix_info%%c_text%Training Mode %c_muted%» %status%");
            player.sendMessage(plugin.parseMessage(raw.replace("%status%", statusStr)));
            return true;
        }

        // Check if matching specific option (e.g. /training totem, /training blocks, /training reload, etc.)
        TrainingOption opt = resolveOption(sub);
        if (opt != null) {
            boolean newState;
            if (args.length >= 2) {
                if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("enable")) {
                    newState = true;
                } else if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("disable")) {
                    newState = false;
                } else {
                    newState = !trainingManager.isOptionEnabled(player.getUniqueId(), opt);
                }
            } else {
                newState = !trainingManager.isOptionEnabled(player.getUniqueId(), opt);
            }

            trainingManager.setOptionEnabled(player.getUniqueId(), opt, newState);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, newState ? 1.2f : 0.9f);

            String statusStr = newState
                    ? plugin.getConfig().getString("training.messages.status-enabled", "%c_success%<b>✔ ᴇɴᴀʙʟᴇᴅ</b>")
                    : plugin.getConfig().getString("training.messages.status-disabled", "%c_error%<b>✖ ᴅɪsᴀʙʟᴇᴅ</b>");
            String raw = plugin.getConfig().getString(
                    "training.messages.option-toggled",
                    "%prefix_info%%c_primary%<b>%option%</b> %c_muted%» %status%"
            );
            player.sendMessage(plugin.parseMessage(raw.replace("%option%", opt.getDisplayName()).replace("%status%", statusStr)));
            return true;
        }

        player.sendMessage(plugin.parseMessage("%prefix_info%%c_text%ᴜsᴀɢᴇ %c_muted%» %c_primary%/training [gui|toggle|on|off|<option>]"));
        return true;
    }

    private TrainingOption resolveOption(String input) {
        for (TrainingOption opt : TrainingOption.values()) {
            if (opt.getKey().equalsIgnoreCase(input)
                    || opt.name().equalsIgnoreCase(input)
                    || opt.getKey().replace("-", "").equalsIgnoreCase(input.replace("-", ""))) {
                return opt;
            }
        }
        if (input.equals("god") || input.equals("godmode") || input.equals("invulnerable") || input.equals("immortal")) return TrainingOption.GOD_MODE;
        if (input.equals("autorefill") || input.equals("refill") || input.equals("cart") || input.equals("rail") || input.equals("projectiles") || input.equals("pearl") || input.equals("pearls")) return TrainingOption.AUTO_REFILL;
        if (input.equals("totem")) return TrainingOption.INFINITE_TOTEM;
        if (input.equals("crossbow") || input.equals("reload")) return TrainingOption.CROSSBOW_RELOAD;
        if (input.equals("blocks") || input.equals("block")) return TrainingOption.INFINITE_BLOCKS;
        if (input.equals("consumables") || input.equals("food") || input.equals("potions")) return TrainingOption.INFINITE_CONSUMABLES;
        if (input.equals("durability")) return TrainingOption.INFINITE_DURABILITY;
        if (input.equals("keepinv") || input.equals("keepinventory")) return TrainingOption.KEEP_INVENTORY;
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> subs = List.of(
                    "gui", "toggle", "on", "off", "godmode", "autorefill",
                    "totem", "crossbow", "blocks", "consumables", "durability", "keepinventory"
            );
            for (String sub : subs) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String optInput = args[0].toLowerCase();
            if (resolveOption(optInput) != null) {
                String input = args[1].toLowerCase();
                for (String val : List.of("on", "off", "toggle")) {
                    if (val.startsWith(input)) {
                        completions.add(val);
                    }
                }
            }
        }
        return completions;
    }
}
