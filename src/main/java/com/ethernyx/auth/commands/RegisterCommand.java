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

public class RegisterCommand implements CommandExecutor {

    private final EtherNyxAuth plugin;

    public RegisterCommand(EtherNyxAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 2) {
            player.sendMessage(plugin.prefix() + ChatColor.RED + "Usage: /register <password> <confirm>");
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());

        // Bug fix #6: Registration exploit — cannot register if already registered.
        if (data.isRegistered()) {
            player.sendMessage(plugin.prefix() + ChatColor.RED + "You are already registered. Use /login instead.");
            return true;
        }

        String password = args[0];
        String confirm = args[1];

        if (!password.equals(confirm)) {
            player.sendMessage(plugin.prefix() + ChatColor.RED + "Passwords do not match.");
            return true;
        }

        String validationError = PasswordUtil.validate(password, plugin.getConfig());
        if (validationError != null) {
            player.sendMessage(plugin.prefix() + ChatColor.RED + validationError);
            return true;
        }

        int strength = plugin.getConfig().getInt("authentication.bcrypt-strength", 12);
        String hash = PasswordUtil.hash(password, strength);

        data.setPasswordHash(hash);
        data.setRegistered(true);
        data.setRegisteredDate(PlayerDataManager.nowIso());
        data.setIp(AuthListener.getIp(player));
        data.setLastLogin(PlayerDataManager.nowIso());
        data.addLogin(PlayerDataManager.nowIso() + ": Registered and logged in");

        plugin.getAuthManager().markLoggedIn(player);
        plugin.getAuthManager().resetAttempts(player.getUniqueId());

        player.sendMessage(plugin.prefix() + ChatColor.GREEN + "Registration successful! You are now logged in.");
        plugin.getPlayerDataManager().save(player.getUniqueId());

        return true;
    }
}
