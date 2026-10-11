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
        this.scoreManager = new ScoreManager();
        this.killDeathListener = new KillDeathListener(this);
        this.regearListener = new RegearListener(this);

        PluginManager pluginManager = Bukkit.getPluginManager();

        // Dang ky Listeners
        pluginManager.registerEvents(killDeathListener, this);
        pluginManager.registerEvents(new AutoPotListener(this), this);
        pluginManager.registerEvents(regearListener, this);

        // Dang ky PlaceholderAPI neu co
        if (pluginManager.getPlugin("PlaceholderAPI") != null) {
            new VCarterExpansion(this).register();
            getLogger().info("Da ket noi thanh cong voi PlaceholderAPI!");
        }

        getLogger().info("VCarterUtils v1.0.0 da duoc kich hoat thanh cong!");
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
        getLogger().info("VCarterUtils da dung hoat dong.");
    }

    public static VCarterUtils getInstance() {
        return instance;
    }

    public ScoreManager getScoreManager() {
        return scoreManager;
    }
}
