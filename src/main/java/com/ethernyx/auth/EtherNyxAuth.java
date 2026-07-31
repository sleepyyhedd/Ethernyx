package com.ethernyx.auth;

import com.ethernyx.auth.commands.*;
import com.ethernyx.auth.listeners.*;
import com.ethernyx.auth.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class EtherNyxAuth extends JavaPlugin {

    private static EtherNyxAuth instance;

    private PlayerDataManager playerDataManager;
    private BanManager banManager;
    private AuthManager authManager;
    private AchievementManager achievementManager;
    private DiscordManager discordManager;
    private BookManager bookManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        reloadConfig();

        // Managers (order matters: data managers first)
        this.playerDataManager = new PlayerDataManager(this);
        this.banManager = new BanManager(this);
        this.discordManager = new DiscordManager(this);
        this.achievementManager = new AchievementManager(this);
        this.bookManager = new BookManager(this);
        this.authManager = new AuthManager(this);

        playerDataManager.load();
        banManager.load();

        registerListeners();
        registerCommands();

        // Auto-save task
        int saveIntervalMinutes = getConfig().getInt("performance.save-interval", 5);
        long ticks = 20L * 60L * saveIntervalMinutes;
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            playerDataManager.saveAll();
            banManager.save();
        }, ticks, ticks);

        // Auto-unban check task
        int unbanIntervalMinutes = getConfig().getInt("ban.auto-unban-interval", 1);
        long unbanTicks = 20L * 60L * unbanIntervalMinutes;
        Bukkit.getScheduler().runTaskTimer(this, () -> banManager.checkExpiredBans(), unbanTicks, unbanTicks);

        getLogger().info("☾ EtherNyxAuth has awakened. ☾");
    }

    @Override
    public void onDisable() {
        if (authManager != null) {
            authManager.cleanupAll();
        }
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        if (banManager != null) {
            banManager.save();
        }
        getLogger().info("☾ EtherNyxAuth has returned to slumber. ☾");
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new AuthListener(this), this);
        pm.registerEvents(new RestrictionListener(this), this);
        pm.registerEvents(new StatsListener(this), this);
        pm.registerEvents(new BanListener(this), this);
        if (getServer().getPluginManager().isPluginEnabled("floodgate")) {
            pm.registerEvents(new FloodgateListener(this), this);
        }
    }

    private void registerCommands() {
        getCommand("register").setExecutor(new RegisterCommand(this));
        getCommand("login").setExecutor(new LoginCommand(this));
        getCommand("changepassword").setExecutor(new ChangePasswordCommand(this));
        AuthCommand authCommand = new AuthCommand(this);
        getCommand("auth").setExecutor(authCommand);
        getCommand("auth").setTabCompleter(authCommand);
    }

    public String prefix() {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("settings.prefix", "&8[&5☾&8] &7"));
    }

    public static EtherNyxAuth getInstance() {
        return instance;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public BanManager getBanManager() {
        return banManager;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    public AchievementManager getAchievementManager() {
        return achievementManager;
    }

    public DiscordManager getDiscordManager() {
        return discordManager;
    }

    public BookManager getBookManager() {
        return bookManager;
    }
}
