package com.ethernyx.auth.commands;

import com.ethernyx.auth.EtherNyxAuth;
import com.ethernyx.auth.models.PlayerData;
import com.ethernyx.auth.util.PasswordUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChangePasswordCommand implements CommandExecutor {

    private final EtherNyxAuth plugin;

    public ChangePasswordCommand(EtherNyxAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        Player player = (Player) sender;

        // Bug fix #7: password change without login must be blocked.
        if (!plugin.getAuthManager().isLoggedIn(player.getUniqueId())) {
            player.sendMessage(plugin.prefix() + ChatColor.RED + "You must be logged in to change your password.");
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(plugin.prefix() + ChatColor.RED + "Usage: /changepassword <old> <new>");
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data == null || !data.isRegistered()) {
            player.sendMessage(plugin.prefix() + ChatColor.RED + "You are not registered.");
            return true;
        }

        String oldPassword = args[0];
        String newPassword = args[1];

        if (!PasswordUtil.matches(oldPassword, data.getPasswordHash())) {
            player.sendMessage(plugin.prefix() + ChatColor.RED + "Your current password is incorrect.");
            return true;
        }

        String validationError = PasswordUtil.validate(newPassword, plugin.getConfig());
        if (validationError != null) {
            player.sendMessage(plugin.prefix() + ChatColor.RED + validationError);
            return true;
        }

        int strength = plugin.getConfig().getInt("authentication.bcrypt-strength", 12);
        data.setPasswordHash(PasswordUtil.hash(newPassword, strength));

        player.sendMessage(plugin.prefix() + ChatColor.GREEN + "Password changed successfully.");
        plugin.getPlayerDataManager().save(player.getUniqueId());

        return true;
    }
}
