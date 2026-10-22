package me.nh4n.vcarterutils.command;

import me.nh4n.vcarterutils.VCarterUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MainCommand implements CommandExecutor, TabCompleter {

    private final VCarterUtils plugin;
    private final MiniMessage miniMessage;

    public MainCommand(VCarterUtils plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("vcarterutils.admin")) {
                String noPerm = plugin.getConfig().getString(
                        "score.messages.no-permission",
                        "<glyph:warning_blue> <#f38ba8>You do not have permission."
                );
                sender.sendMessage(miniMessage.deserialize(noPerm));
                return true;
            }

            plugin.reloadPluginConfig();

            String successMsg = plugin.getConfig().getString(
                    "admin.messages.reload-success",
                    "<glyph:warning_blue> <#a6e3a1>VCarterUtils configuration reloaded."
            );
            sender.sendMessage(miniMessage.deserialize(successMsg));
            return true;
        }

        sender.sendMessage(miniMessage.deserialize(
                "<glyph:warning_blue> <#a6adc8>VCarterUtils <#6c7086>| <#89dceb>/" + label + " reload"
        ));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("vcarterutils.admin") && "reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
        }
        return completions;
    }
}
```

---

### 8. [CẬP NHẬT] `src/main/resources/config.yml`

```yaml
# ==============================================================
#                 VCarterUtils Configuration
#       Catppuccin Pastel Palette & Glyph Integration
# ==============================================================

autopot:
  enabled: true
  blacklisted-worlds:
    - "ffa_diamond"

totem:
  enabled: true
  max-in-inventory: 1
  max-activations-per-kit: 1
  excess-action: "REMOVE"
  blacklisted-worlds:
    - "ffa_diamond"
  messages:
    inventory-limit-reached: "<glyph:warning_blue> <#f38ba8>You can only carry <#89dceb><b>%max%</b> <#f38ba8>Totem in your inventory."
    activation-limit-reached: "<glyph:warning_blue> <#f38ba8>You already used your Totem for this kit."

# ==============================================================
#                 ENDER PEARL COOLDOWN SYSTEM
# ==============================================================
pearl-cooldown:
  enabled: true
  # Muc mac dinh (1s). Nguoi choi KHONG BAO GIO chinh duoc thap hon muc nay.
  default-seconds: 1
  available-options:
    - 1
    - 3
    - 5
    - 6
    - 8
    - 10
    - 15
  clear-on-respawn: true
  clear-on-kit-load: true
  send-chat-on-change: true

  global-force:
    enabled: false
    seconds: 1

  force-worlds:
    enabled: true
    worlds:
      ffa_diamond: 10

  messages:
    cooldown-changed: "<glyph:warning_blue> <#a6adc8>Pearl cooldown set to <#89dceb><b>%seconds%s</b><#a6adc8>."
    invalid-option: "<glyph:warning_blue> <#f38ba8>Invalid cooldown! Available: <#89dceb>%options%"
    world-status-normal: "<#a6e3a1>● Personal setting active"
    world-status-forced: "<#f9e2af>● World forced: <#89dceb><b>%forced%s</b>"
    world-status-global-forced: "<#f9e2af>● Global forced: <#89dceb><b>%forced%s</b>"

# ==============================================================
#                     MACE COOLDOWN SYSTEM
# ==============================================================
mace-cooldown:
  enabled: true
  # Muc mac dinh (1s). Nguoi choi KHONG BAO GIO chinh duoc thap hon muc nay.
  default-seconds: 1
  available-options:
    - 1
    - 3
    - 5
    - 6
    - 8
    - 10
    - 15
  only-players: false
  only-smash-attack: false
  cancel-attack-on-cooldown: true
  clear-on-respawn: true
  clear-on-kit-load: true
  send-chat-on-change: true

  global-force:
    enabled: false
    seconds: 1

  force-worlds:
    enabled: true
    worlds:
      ffa_diamond: 10

  messages:
    cooldown-changed: "<glyph:warning_blue> <#a6adc8>Mace cooldown set to <#89dceb><b>%seconds%s</b><#a6adc8>."
    invalid-option: "<glyph:warning_blue> <#f38ba8>Invalid cooldown! Available: <#89dceb>%options%"
    on-cooldown: "<glyph:warning_blue> <#f38ba8>Mace is on cooldown for <#f9e2af><b>%seconds%s</b><#f38ba8>."
    world-status-normal: "<#a6e3a1>● Personal setting active"
    world-status-forced: "<#f9e2af>● World forced: <#89dceb><b>%forced%s</b>"
    world-status-global-forced: "<#f9e2af>● Global forced: <#89dceb><b>%forced%s</b>"

# ==============================================================
#                 CUSTOMIZABLE /SETTING MENU
# ==============================================================
setting-menu:
  title: "<#6c7086>✦ <#89dceb><b>Settings</b> <#6c7086>✦"
  rows: 3
  filler:
    enabled: true
    material: "GRAY_STAINED_GLASS_PANE"
    name: " "
  # Types: PEARL_COOLDOWN, MACE_COOLDOWN, ADMIN_MENU, CLOSE, COMMAND, DECORATION
  items:
    pearl-cooldown:
      enabled: true
      type: "PEARL_COOLDOWN"
      slot: 11
      material: "ENDER_PEARL"
      show-seconds-as-amount: true
      name: "<!italic><#89dceb><b>Pearl Cooldown</b>"
      lore:
        - ""
        - "<!italic><#a6adc8>Personal: <#89dceb><b>%current%s</b> <#6c7086>(Default: %default%s)"
        - "<!italic><#a6adc8>Effective: <#f9e2af><b>%effective%s</b>"
        - "<!italic>%world_status%"
        - ""
        - "<!italic><#a6adc8>Presets: %options_list%"
        - ""
        - "<!italic><white><glyph:left_click></white> <#a6e3a1>Increase <#6c7086>| <white><glyph:right_click></white> <#f9e2af>Decrease"
        - "<!italic><#f38ba8>Shift + <white><glyph:left_click></white> <#a6adc8>Reset default"
    mace-cooldown:
      enabled: true
      type: "MACE_COOLDOWN"
      slot: 15
      material: "MACE"
      show-seconds-as-amount: true
      name: "<!italic><#89dceb><b>Mace Cooldown</b>"
      lore:
        - ""
        - "<!italic><#a6adc8>Personal: <#89dceb><b>%current%s</b> <#6c7086>(Default: %default%s)"
        - "<!italic><#a6adc8>Effective: <#f9e2af><b>%effective%s</b>"
        - "<!italic>%world_status%"
        - ""
        - "<!italic><#a6adc8>Presets: %options_list%"
        - ""
        - "<!italic><white><glyph:left_click></white> <#a6e3a1>Increase <#6c7086>| <white><glyph:right_click></white> <#f9e2af>Decrease"
        - "<!italic><#f38ba8>Shift + <white><glyph:left_click></white> <#a6adc8>Reset default"
    admin-panel:
      enabled: true
      type: "ADMIN_MENU"
      slot: 4
      material: "COMPARATOR"
      name: "<!italic><#f38ba8><b>Admin Manager</b>"
      lore:
        - ""
        - "<!italic><#a6adc8>Configure Global & Per-World"
        - "<!italic><#a6adc8>cooldown overrides."
        - ""
        - "<!italic><white><glyph:left_click></white> <#89dceb>Open Admin GUI"
    close-button:
      enabled: true
      type: "CLOSE"
      slot: 22
      material: "BARRIER"
      name: "<!italic><#f38ba8><b>Close</b>"
      lore:
        - "<!italic><white><glyph:left_click></white> <#6c7086>Close menu"

# ==============================================================
#                      BACK ON DEATH FEATURE
# ==============================================================
back:
  enabled: true
  blacklisted-worlds:
    - "ffa_diamond"
  messages:
    death-notify: "<glyph:warning_blue> <#a6adc8>You died! Click <click:run_command:'/back'><hover:show_text:'<#a6e3a1>Click to teleport back'><#89dceb><b>[HERE]</b></hover></click> <#a6adc8>or type <#89dceb>/back<#a6adc8>."
    teleported: "<glyph:warning_blue> <#a6e3a1>Teleported to your last death location."
    no-death-location: "<glyph:warning_blue> <#f38ba8>No previous death location found."
    world-blacklisted: "<glyph:warning_blue> <#f38ba8>You cannot use <#89dceb>/back <#f38ba8>in this world."

# ==============================================================
#                      SHULKER REGEAR SYSTEM
# ==============================================================
regear:
  damage-cooldown-seconds: 5
  cooldown-seconds: 3
  clear-junk-in-empty-slots: true
  announcement:
    enabled: true
    broadcast: true
    message: "<glyph:warning_blue> <#89dceb><b>%player%</b> <#a6adc8>regeared"

  whitelist:
    - ENDER_PEARL
    - TNT_MINECART
    - SPLASH_POTION
    - CROSSBOW
    - ELYTRA
    - COBWEB
    - ARROWS
    - AXES
    - PICKAXES
    - PLANKS
    - WOOD
    - RAILS

  item:
    name: "<!italic><#89dceb><b>Shulker Regear</b>"
    lore:
      - "<!italic><white><glyph:right_click></white> <#a6adc8>Place to restock inventory"
      - "<!italic><#6c7086>Auto-removes after placement"

  messages:
    already-have-shulker: "<glyph:warning_blue> <#f38ba8>You already have a <#89dceb>Regear Shulker<#f38ba8>."
    inventory-full: "<glyph:warning_blue> <#f38ba8>Inventory is full! Free up at least 1 slot."
    received-shulker: "<glyph:warning_blue> <#a6e3a1>Received <#89dceb><b>Shulker Regear</b><#a6e3a1>. Place it to restock."
    combat-tagged: "<glyph:warning_blue> <#f38ba8>In combat! Wait <#f9e2af><b>%seconds%s</b> <#f38ba8>to regear."
    on-cooldown: "<glyph:warning_blue> <#f38ba8>Regear is on cooldown for <#f9e2af><b>%seconds%s</b><#f38ba8>."
    no-kit-loaded: "<glyph:warning_blue> <#f38ba8>Load a kit first <#89dceb>(/k1)<#f38ba8>."
    success: "<glyph:warning_blue> <#a6e3a1>Inventory restocked."

# ==============================================================
#                        1V1 SCORE SYSTEM
# ==============================================================
score:
  auto-reset:
    enabled: true
    interval-minutes: 60
    broadcast: true
  request-timeout-seconds: 60
  messages:
    no-permission: "<glyph:warning_blue> <#f38ba8>You do not have permission."
    player-not-found: "<glyph:warning_blue> <#f38ba8>Player <#89dceb>%target% <#f38ba8>is not online."
    cannot-target-self: "<glyph:warning_blue> <#f38ba8>You cannot target yourself."
    request-sent: "<glyph:warning_blue> <#a6e3a1>Sent score reset request to <#89dceb><b>%target%</b><#a6e3a1>."
    request-received: "<glyph:warning_blue> <#89dceb><b>%sender%</b> <#a6adc8>wants to reset 1v1 score: <click:run_command:'/score accept %sender%'><hover:show_text:'<#a6e3a1>Click to accept'><#a6e3a1><b>[ACCEPT]</b></hover></click> <click:run_command:'/score decline %sender%'><hover:show_text:'<#f38ba8>Click to decline'><#f38ba8><b>[DECLINE]</b></hover></click>"
    no-pending-request: "<glyph:warning_blue> <#f38ba8>No pending request from <#89dceb>%sender%<#f38ba8>."
    request-declined: "<glyph:warning_blue> <#f38ba8>Reset request with <#89dceb>%player% <#f38ba8>was declined."
    score-reset-success: "<glyph:warning_blue> <#a6e3a1>1v1 score reset between <#89dceb><b>%player1%</b> <#a6e3a1>and <#89dceb><b>%player2%</b><#a6e3a1>."
    global-reset-admin: "<glyph:warning_blue> <#a6e3a1>All 1v1 scores have been reset by an admin."
    global-reset-auto: "<glyph:warning_blue> <#a6e3a1>All 1v1
