package com.ethernyx.auth.commands;

import com.ethernyx.auth.EtherNyxAuth;
import com.ethernyx.auth.listeners.AuthListener;
import com.ethernyx.auth.managers.PlayerDataManager;
import com.ethernyx.auth.models.PlayerData;
import com.ethernyx.auth.util.PasswordUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LoginCommand implements CommandExecutor {

    private final EtherNyxAuth plugin;

    public LoginCommand(EtherNyxAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        Player player = (Player) sender;

        if (plugin.getAuthManager().isLoggedIn(player.getUniqueId())) {
            player.sendMessage(plugin.prefix() + ChatColor.YELLOW + "You are already logged in.");
            return true;
        }

        if (args.length < 1 || args.length > 2) {
            player.sendMessage(plugin.prefix() + ChatColor.RED + "Usage: /login <password> [remember:true/false]");
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data == null || !data.isRegistered()) {
            player.sendMessage(plugin.prefix() + ChatColor.RED + "You are not registered. Use /register <password> <confirm>");
            return true;
        }

        // Bug fix #5: brute force protection — cooldown between attempts.
        if (plugin.getAuthManager().isOnCooldown(player.getUniqueId())) {
            long remainingMs = plugin.getAuthManager().getCooldownRemainingMillis(player.getUniqueId());
            player.sendMessage(plugin.prefix() + ChatColor.RED
                    + "Please wait " + (remainingMs / 1000 + 1) + "s before trying again.");
            return true;
        }

        String password = args[0];
        boolean rememberRequested = args.length == 2 && parseRemember(args[1]);

        if (!PasswordUtil.matches(password, data.getPasswordHash())) {
            int attempts = plugin.getAuthManager().registerFailedAttempt(player.getUniqueId());
            int maxAttempts = plugin.getConfig().getInt("authentication.max-attempts", 5);

            if (attempts >= maxAttempts) {
                player.kickPlayer(plugin.prefix() + ChatColor.RED + "Too many failed login attempts.");
                return true;
            }

            player.sendMessage(plugin.prefix() + ChatColor.RED
                    + "Incorrect password. (" + attempts + "/" + maxAttempts + " attempts)");
            return true;
        }

        // Correct password.
        String currentIp = AuthListener.getIp(player);
        data.setIp(currentIp);
        data.setLastLogin(PlayerDataManager.nowIso());
        data.addLogin(PlayerDataManager.nowIso() + ": Joined and logged in");

        boolean rememberEnabled = plugin.getConfig().getBoolean("authentication.remember-me-enabled", true);
        if (rememberEnabled && rememberRequested) {
            data.setRememberMe(true);
            data.setRememberIp(currentIp);
        } else if (args.length == 2) {
            // Explicit false, or remember-me disabled server-side.
            data.setRememberMe(false);
            data.setRememberIp(null);
        }

        plugin.getAuthManager().setPendingIpConfirmation(player.getUniqueId(), false);
        plugin.getAuthManager().markLoggedIn(player);

        // Remove restriction effects now that the player is authenticated.
        AuthListener.removeLoginRestrictions0(player);

        player.sendMessage(plugin.prefix() + ChatColor.GREEN + "Login successful. Welcome back!");
        plugin.getPlayerDataManager().save(player.getUniqueId());

        return true;
    }

    private boolean parseRemember(String value) {
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes");
    }
}
