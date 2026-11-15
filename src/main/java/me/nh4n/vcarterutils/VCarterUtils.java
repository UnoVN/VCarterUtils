package me.nh4n.vcarterutils;

import me.nh4n.vcarterutils.command.BackCommand;
import me.nh4n.vcarterutils.command.MainCommand;
import me.nh4n.vcarterutils.command.ScoreCommand;
import me.nh4n.vcarterutils.command.SettingCommand;
import me.nh4n.vcarterutils.command.TrainingCommand;
import me.nh4n.vcarterutils.config.ConfigManager;
import me.nh4n.vcarterutils.hook.ActionBarFilter;
import me.nh4n.vcarterutils.hook.NexoHook;
import me.nh4n.vcarterutils.hook.VCarterExpansion;
import me.nh4n.vcarterutils.listener.AntiInterruptListener;
import me.nh4n.vcarterutils.listener.AutoPotListener;
import me.nh4n.vcarterutils.listener.BackListener;
import me.nh4n.vcarterutils.listener.CombatTagListener;
import me.nh4n.vcarterutils.listener.KillDeathListener;
import me.nh4n.vcarterutils.listener.LobbyHotbarListener;
import me.nh4n.vcarterutils.listener.MaceListener;
import me.nh4n.vcarterutils.listener.PearlListener;
import me.nh4n.vcarterutils.listener.RegearListener;
import me.nh4n.vcarterutils.listener.SettingMenuListener;
import me.nh4n.vcarterutils.listener.TotemListener;
import me.nh4n.vcarterutils.listener.TrainingListener;
import me.nh4n.vcarterutils.listener.XbowListener;
import me.nh4n.vcarterutils.manager.BackManager;
import me.nh4n.vcarterutils.manager.CombatTagManager;
import me.nh4n.vcarterutils.manager.LobbyHotbarManager;
import me.nh4n.vcarterutils.manager.MaceManager;
import me.nh4n.vcarterutils.manager.PearlManager;
import me.nh4n.vcarterutils.manager.RegearManager;
import me.nh4n.vcarterutils.manager.ScoreManager;
import me.nh4n.vcarterutils.manager.SettingMenuManager;
import me.nh4n.vcarterutils.manager.TotemManager;
import me.nh4n.vcarterutils.manager.TrainingManager;
import me.nh4n.vcarterutils.manager.XbowManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public final class VCarterUtils extends JavaPlugin {

    private static VCarterUtils instance;
    private ConfigManager configManager;
    private ScoreManager scoreManager;
    private RegearManager regearManager;
    private TotemManager totemManager;
    private BackManager backManager;
    private PearlManager pearlManager;
    private MaceManager maceManager;
    private LobbyHotbarManager lobbyHotbarManager;
    private XbowManager xbowManager;
    private SettingMenuManager settingMenuManager;
    private CombatTagManager combatTagManager;
    private TrainingManager trainingManager;

    private KillDeathListener killDeathListener;
    private AutoPotListener autoPotListener;
    private RegearListener regearListener;
    private TotemListener totemListener;
    private BackListener backListener;
    private PearlListener pearlListener;
    private MaceListener maceListener;
    private LobbyHotbarListener lobbyHotbarListener;
    private XbowListener xbowListener;
    private SettingMenuListener settingMenuListener;
    private CombatTagListener combatTagListener;
    private AntiInterruptListener antiInterruptListener;
    private ActionBarFilter actionBarFilter;
    private TrainingListener trainingListener;

    private ScoreCommand scoreCommand;
    private MainCommand mainCommand;
    private BackCommand backCommand;
    private SettingCommand settingCommand;
    private TrainingCommand trainingCommand;
    private BukkitTask autoResetTask;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        getConfig().setDefaults(new YamlConfiguration());
        this.configManager = new ConfigManager(this);
        this.configManager.loadAll();

        NexoHook.init(this);
        checkAndMigrateTrainingConfig();

        this.scoreManager = new ScoreManager();
        this.regearManager = new RegearManager(this);
        this.totemManager = new TotemManager(this);
        this.backManager = new BackManager(this);
        this.pearlManager = new PearlManager(this);
        this.maceManager = new MaceManager(this);
        this.lobbyHotbarManager = new LobbyHotbarManager(this);
        this.xbowManager = new XbowManager(this);
        this.settingMenuManager = new SettingMenuManager(this);
        this.combatTagManager = new CombatTagManager(this);
        this.trainingManager = new TrainingManager(this);

        this.killDeathListener = new KillDeathListener(this);
        this.autoPotListener = new AutoPotListener(this);
        this.regearListener = new RegearListener(this);
        this.totemListener = new TotemListener(this);
        this.backListener = new BackListener(this);
        this.pearlListener = new PearlListener(this);
        this.maceListener = new MaceListener(this);
        this.lobbyHotbarListener = new LobbyHotbarListener(this);
        this.xbowListener = new XbowListener(this);
        this.settingMenuListener = new SettingMenuListener(this);
        this.combatTagListener = new CombatTagListener(this, combatTagManager);
        this.combatTagListener.register();
        this.antiInterruptListener = new AntiInterruptListener(this, combatTagManager);
        this.actionBarFilter = new ActionBarFilter(this);
        this.actionBarFilter.register();
        this.trainingListener = new TrainingListener(this, trainingManager);

        this.scoreCommand = new ScoreCommand(this);
        this.mainCommand = new MainCommand(this);
        this.backCommand = new BackCommand(this);
        this.settingCommand = new SettingCommand(this);
        this.trainingCommand = new TrainingCommand(this);

        PluginManager pluginManager = Bukkit.getPluginManager();

        pluginManager.registerEvents(killDeathListener, this);
        pluginManager.registerEvents(autoPotListener, this);
        pluginManager.registerEvents(regearListener, this);
        pluginManager.registerEvents(totemListener, this);
        pluginManager.registerEvents(backListener, this);
        pluginManager.registerEvents(pearlListener, this);
        pluginManager.registerEvents(maceListener, this);
        pluginManager.registerEvents(lobbyHotbarListener, this);
        pluginManager.registerEvents(xbowListener, this);
        pluginManager.registerEvents(settingMenuListener, this);
        pluginManager.registerEvents(antiInterruptListener, this);
        pluginManager.registerEvents(trainingListener, this);
        pluginManager.registerEvents(scoreCommand, this);

        PluginCommand scoreCmd = getCommand("score");
        if (scoreCmd != null) {
            scoreCmd.setExecutor(scoreCommand);
            scoreCmd.setTabCompleter(scoreCommand);
        }

        PluginCommand vcuCmd = getCommand("vcarterutils");
        if (vcuCmd != null) {
            vcuCmd.setExecutor(mainCommand);
            vcuCmd.setTabCompleter(mainCommand);
        }

        PluginCommand backCmd = getCommand("back");
        if (backCmd != null) {
            backCmd.setExecutor(backCommand);
            backCmd.setTabCompleter(backCommand);
        }

        PluginCommand settingCmd = getCommand("setting");
        if (settingCmd != null) {
            settingCmd.setExecutor(settingCommand);
            settingCmd.setTabCompleter(settingCommand);
        }

        PluginCommand trainCmd = getCommand("training");
        if (trainCmd != null) {
            trainCmd.setExecutor(trainingCommand);
            trainCmd.setTabCompleter(trainingCommand);
        }

        setupAutoResetTask();

        if (pluginManager.getPlugin("PlaceholderAPI") != null) {
            new VCarterExpansion(this).register();
            getLogger().info("Successfully hooked into PlaceholderAPI!");
        }

        if (pluginManager.getPlugin("FoCombatTag") != null) {
            getLogger().info("Successfully hooked into FoCombatTag!");
        }

        getLogger().info("VCarterUtils v" + getDescription().getVersion() + " has been successfully enabled.");
    }

    @Override
    public void saveConfig() {
        if (configManager != null) {
            configManager.saveAll();
        } else {
            super.saveConfig();
        }
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        getConfig().setDefaults(new YamlConfiguration());
        if (configManager != null) {
            configManager.reloadAll();
        }
    }

    public void reloadPluginConfig() {
        if (configManager != null) {
            configManager.reloadAll();
        } else {
            reloadConfig();
        }

        if (regearManager != null) {
            regearManager.reload();
        }
        if (totemManager != null) {
            totemManager.reload();
        }
        if (backManager != null) {
            backManager.reload();
        }
        if (pearlManager != null) {
            pearlManager.reload();
        }
        if (maceManager != null) {
            maceManager.reload();
        }
        if (lobbyHotbarManager != null) {
            lobbyHotbarManager.reload();
        }
        if (xbowManager != null) {
            xbowManager.reload();
        }
        if (settingMenuManager != null) {
            settingMenuManager.reload();
        }
        if (combatTagManager != null) {
            combatTagManager.reload();
        }
        if (trainingManager != null) {
            trainingManager.reload();
        }
        setupAutoResetTask();
        getLogger().info("VCarterUtils configuration reloaded.");
    }

    private void setupAutoResetTask() {
        if (autoResetTask != null) {
            autoResetTask.cancel();
            autoResetTask = null;
        }

        if (!getConfig().getBoolean("score.auto-reset.enabled", true)) {
            return;
        }

        long minutes = getConfig().getLong("score.auto-reset.interval-minutes", 60L);
        long ticks = minutes * 60L * 20L;

        this.autoResetTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            scoreManager.clearAll();

            if (getConfig().getBoolean("score.auto-reset.broadcast", true)) {
                String msg = getConfig().getString("score.messages.global-reset-auto",
                        "%prefix_info%%c_primary%<b>sᴄᴏʀᴇ</b> %c_muted%» %c_text%All 1v1 scores have been automatically reset.");
                Bukkit.broadcast(parseMessage(msg));
            }
        }, ticks, ticks);
    }

    private void checkAndMigrateTrainingConfig() {
        boolean changed = false;
        if (!getConfig().contains("training")) {
            getConfig().set("training.enabled", true);
            getConfig().set("training.disable-in-combat", true);
            getConfig().set("training.blacklisted-worlds", List.of("ffa_diamond"));
            getConfig().set("training.messages.toggled", "%prefix_info%%c_text%ᴍᴀsᴛᴇʀ ᴛʀᴀɪɴɪɴɢ %c_muted%» %status%");
            getConfig().set("training.messages.status-enabled", "%c_success%<b>✔ ᴇɴᴀʙʟᴇᴅ</b>");
            getConfig().set("training.messages.status-disabled", "%c_error%<b>✖ ᴅɪsᴀʙʟᴇᴅ</b>");
            getConfig().set("training.messages.cannot-in-combat", "%prefix_combat%%c_error%<b>ɪɴ ᴄᴏᴍʙᴀᴛ</b> %c_muted%» %c_text%Training Mode cannot be used during combat.");
            getConfig().set("training.messages.auto-disabled-combat", "%prefix_combat%%c_error%<b>ᴛʀᴀɪɴɪɴɢ ᴅɪsᴀʙʟᴇᴅ</b> %c_muted%» %c_text%Automatically turned off due to PvP combat.");
            getConfig().set("training.messages.blacklisted-world", "%prefix_warning%%c_purple%<b>ʀᴇsᴛʀɪᴄᴛᴇᴅ</b> %c_muted%» %c_text%Training Mode is disabled in this world.");
            getConfig().set("training.messages.option-toggled", "%prefix_info%%c_primary%<b>%option%</b> %c_muted%» %status%");
            changed = true;
        }

        if (getConfig().getInt("setting-menu.items.xbow-counter-toggle.slot", -1) == 13) {
            getConfig().set("setting-menu.items.xbow-counter-toggle.slot", 14);
            changed = true;
        }
        if (getConfig().getInt("setting-menu.items.lobby-hotbar-toggle.slot", -1) == 14) {
            getConfig().set("setting-menu.items.lobby-hotbar-toggle.slot", 15);
            changed = true;
        }

        if (!getConfig().contains("setting-menu.items.training-mode")) {
            getConfig().set("setting-menu.items.training-mode.enabled", true);
            getConfig().set("setting-menu.items.training-mode.type", "TRAINING_MODE_TOGGLE");
            getConfig().set("setting-menu.items.training-mode.slot", 13);
            getConfig().set("setting-menu.items.training-mode.material", "TARGET");
            getConfig().set("setting-menu.items.training-mode.name", "<!italic><white><sprite:items:item/totem_of_undying></white> <#89dceb><b>Training Mode</b>");
            getConfig().set("setting-menu.items.training-mode.lore", List.of(
                    "<!italic><#6c7086>ᴘʀᴀᴄᴛɪᴄᴇ & sᴀɴᴅʙᴏx",
                    "",
                    "<!italic><#6c7086>▪ <#a6adc8>sᴛᴀᴛᴜs: %training_status%",
                    "",
                    "<!italic><#a6adc8>Infinite totem, blocks, auto-refill,",
                    "<!italic><#a6adc8>consumables, and crossbow auto-reload.",
                    "",
                    "<!italic><white><glyph:left_click></white> <#89dceb>ᴏᴘᴛɪᴏɴs <#6c7086>│ <white><glyph:right_click></white> <#a6e3a1>ǫᴜɪᴄᴋ ᴛᴏɢɢʟᴇ"
            ));
            changed = true;
        }

        if (!getConfig().contains("training-menu.title")) {
            getConfig().set("training-menu.title", "<#6c7086>✦ %c_primary%<b>ᴛʀᴀɪɴɪɴɢ sᴇᴛᴛɪɴɢs</b> <#6c7086>✦");
            changed = true;
        }

        if (changed) {
            saveConfig();
            getLogger().info("Successfully auto-migrated Training Mode sections into configuration.");
        }
    }

    @Override
    public void onDisable() {
        if (autoResetTask != null) {
            autoResetTask.cancel();
        }
        if (killDeathListener != null) {
            killDeathListener.cancelAllTasks();
        }
        if (autoPotListener != null) {
            autoPotListener.clearAll();
        }
        if (totemManager != null) {
            totemManager.clearAll();
        }
        if (backManager != null) {
            backManager.clearAll();
        }
        if (regearListener != null) {
            regearListener.clearAll();
        }
        if (regearManager != null) {
            regearManager.clearAll();
        }
        if (pearlManager != null) {
            pearlManager.clearAll();
        }
        if (maceManager != null) {
            maceManager.clearAll();
        }
        if (lobbyHotbarManager != null) {
            lobbyHotbarManager.clearAll();
        }
        if (xbowManager != null) {
            xbowManager.clearAll();
        }
        if (xbowListener != null) {
            xbowListener.clearAll();
        }
        if (scoreManager != null) {
            scoreManager.clearAll();
        }
        if (antiInterruptListener != null) {
            antiInterruptListener.cancelTask();
        }
        if (actionBarFilter != null) {
            actionBarFilter.unregisterAll();
        }
        if (combatTagManager != null) {
            combatTagManager.clearAll();
        }
        if (trainingManager != null) {
            trainingManager.clearAll();
        }
        getLogger().info("VCarterUtils has been disabled.");
    }

    public static VCarterUtils getInstance() {
        return instance;
    }

    public ScoreManager getScoreManager() {
        return scoreManager;
    }

    public KillDeathListener getKillDeathListener() {
        return killDeathListener;
    }

    public ActionBarFilter getActionBarFilter() {
        return actionBarFilter;
    }

    public RegearManager getRegearManager() {
        return regearManager;
    }

    public TotemManager getTotemManager() {
        return totemManager;
    }

    public BackManager getBackManager() {
        return backManager;
    }

    public PearlManager getPearlManager() {
        return pearlManager;
    }

    public MaceManager getMaceManager() {
        return maceManager;
    }

    public LobbyHotbarManager getLobbyHotbarManager() {
        return lobbyHotbarManager;
    }

    public XbowManager getXbowManager() {
        return xbowManager;
    }

    public SettingMenuManager getSettingMenuManager() {
        return settingMenuManager;
    }

    public CombatTagManager getCombatTagManager() {
        return combatTagManager;
    }

    public TrainingManager getTrainingManager() {
        return trainingManager;
    }

    public BackCommand getBackCommand() {
        return backCommand;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public String formatMessage(String message) {
        return me.nh4n.vcarterutils.util.MessageUtil.format(this, message);
    }

    public net.kyori.adventure.text.Component parseMessage(String message) {
        return me.nh4n.vcarterutils.util.MessageUtil.parse(this, message);
    }
}
