package me.nh4n.vcarterutils;

import me.nh4n.vcarterutils.command.ScoreCommand;
import me.nh4n.vcarterutils.hook.VCarterExpansion;
import me.nh4n.vcarterutils.listener.AutoPotListener;
import me.nh4n.vcarterutils.listener.KillDeathListener;
import me.nh4n.vcarterutils.listener.RegearListener;
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
    private KillDeathListener killDeathListener;
    private AutoPotListener autoPotListener;
    private RegearListener regearListener;
    private BukkitTask autoResetTask;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.scoreManager = new ScoreManager();
        this.killDeathListener = new KillDeathListener(this);
        this.autoPotListener = new AutoPotListener(this);
        this.regearListener = new RegearListener(this);

        PluginManager pluginManager = Bukkit.getPluginManager();

        // Dang ky Listeners
        pluginManager.registerEvents(killDeathListener, this);
        pluginManager.registerEvents(autoPotListener, this);
        pluginManager.registerEvents(regearListener, this);

        // Dang ky Lenh /score
        ScoreCommand scoreCommand = new ScoreCommand(this);
        PluginCommand cmd = getCommand("score");
        if (cmd != null) {
            cmd.setExecutor(scoreCommand);
            cmd.setTabCompleter(scoreCommand);
        }

        // Dang ky Task tu dong Reset Score theo chu ky (Mac dinh 60 phut = 1 tieng)
        setupAutoResetTask();

        // Ket noi PlaceholderAPI
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
                String msg = getConfig().getString("score.messages.global-reset-auto");
                if (msg != null && !msg.isEmpty()) {
                    Bukkit.broadcast(MiniMessage.miniMessage().deserialize(msg));
                }
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
        if (regearListener != null) {
            regearListener.clearAll();
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
}
