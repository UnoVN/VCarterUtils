package me.nh4n.vcarterutils;

import me.nh4n.vcarterutils.command.ScoreCommand;
import me.nh4n.vcarterutils.hook.VCarterExpansion;
import me.nh4n.vcarterutils.listener.AutoPotListener;
import me.nh4n.vcarterutils.listener.KillDeathListener;
import me.nh4n.vcarterutils.listener.RegearListener;
import me.nh4n.vcarterutils.manager.RegearManager;
import me.nh4n.vcarterutils.manager.ScoreManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class VCarterUtils extends JavaPlugin {

    private static VCarterUtils instance;
    private ScoreManager scoreManager;
    private RegearManager regearManager;
    private KillDeathListener killDeathListener;
    private AutoPotListener autoPotListener;
    private RegearListener regearListener;
    private ScoreCommand scoreCommand;
    private BukkitTask autoResetTask;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        this.scoreManager = new ScoreManager();
        this.regearManager = new RegearManager(this);
        this.killDeathListener = new KillDeathListener(this);
        this.autoPotListener = new AutoPotListener(this);
        this.regearListener = new RegearListener(this);
        this.scoreCommand = new ScoreCommand(this);

        PluginManager pluginManager = Bukkit.getPluginManager();

        pluginManager.registerEvents(killDeathListener, this);
        pluginManager.registerEvents(autoPotListener, this);
        pluginManager.registerEvents(regearListener, this);
        pluginManager.registerEvents(scoreCommand, this);

        PluginCommand cmd = getCommand("score");
        if (cmd != null) {
            cmd.setExecutor(scoreCommand);
            cmd.setTabCompleter(scoreCommand);
        }

        setupAutoResetTask();

        if (pluginManager.getPlugin("PlaceholderAPI") != null) {
            new VCarterExpansion(this).register();
            getLogger().info("Successfully hooked into PlaceholderAPI!");
        }

        getLogger().info("VCarterUtils v1.0.0 has been successfully enabled.");
    }

    private void setupAutoResetTask() {
        if (!getConfig().getBoolean("score.auto-reset.enabled", true)) {
            return;
        }

        long minutes = getConfig().getLong("score.auto-reset.interval-minutes", 60L);
        long ticks = minutes * 60L * 20L;

        this.autoResetTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            scoreManager.clearAll();

            if (getConfig().getBoolean("score.auto-reset.broadcast", true)) {
                String msg = getConfig().getString("score.messages.global-reset-auto",
                        "<glyph:warning_blue> <#a6e3a1>All 1v1 scores have been automatically reset (Hourly Schedule).");
                Bukkit.broadcast(MiniMessage.miniMessage().deserialize(msg));
            }
        }, ticks, ticks);
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
        if (regearManager != null) {
            regearManager.clearAll();
        }
        if (scoreManager != null) {
            scoreManager.clearAll();
        }
        getLogger().info("VCarterUtils has been disabled.");
    }

    public static VCarterUtils getInstance() {
        return instance;
    }

    public ScoreManager getScoreManager() {
        return scoreManager;
    }

    public RegearManager getRegearManager() {
        return regearManager;
    }
}
