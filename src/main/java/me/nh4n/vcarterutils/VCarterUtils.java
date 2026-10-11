package me.nh4n.vcarterutils;

import me.nh4n.vcarterutils.hook.VCarterExpansion;
import me.nh4n.vcarterutils.listener.AutoPotListener;
import me.nh4n.vcarterutils.listener.KillDeathListener;
import me.nh4n.vcarterutils.listener.RegearListener;
import me.nh4n.vcarterutils.manager.ScoreManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class VCarterUtils extends JavaPlugin {

    private static VCarterUtils instance;
    private ScoreManager scoreManager;
    private KillDeathListener killDeathListener;
    private RegearListener regearListener;

    @Override
    public void onEnable() {
        instance = this;

        // Tao file config.yml mac dinh neu chua ton tai
        saveDefaultConfig();

        this.scoreManager = new ScoreManager();
        this.killDeathListener = new KillDeathListener(this);
        this.regearListener = new RegearListener(this);

        PluginManager pluginManager = Bukkit.getPluginManager();

        // Dang ky cac Listeners
        pluginManager.registerEvents(killDeathListener, this);
        pluginManager.registerEvents(new AutoPotListener(this), this);
        pluginManager.registerEvents(regearListener, this);

        // Ket noi PlaceholderAPI
        if (pluginManager.getPlugin("PlaceholderAPI") != null) {
            new VCarterExpansion(this).register();
            getLogger().info("Successfully hooked into PlaceholderAPI!");
        }

        getLogger().info("VCarterUtils v1.0.0 has been successfully enabled.");
    }

    @Override
    public void onDisable() {
        if (killDeathListener != null) {
            killDeathListener.cancelAllTasks();
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
