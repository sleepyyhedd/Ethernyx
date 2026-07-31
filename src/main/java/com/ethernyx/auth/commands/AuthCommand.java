package com.ethernyx.auth.commands;

import com.ethernyx.auth.EtherNyxAuth;
import com.ethernyx.auth.managers.AchievementManager;
import com.ethernyx.auth.models.BanEntry;
import com.ethernyx.auth.models.PlayerData;
import com.ethernyx.auth.util.DurationUtil;
import com.ethernyx.auth.util.PasswordUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class AuthCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList(
            "info", "reset", "bypass", "unbypass", "ban", "tempban", "unban",
            "banlist", "baninfo", "reload"
    );
    private static final List<String> PUBLIC_SUBCOMMANDS = Arrays.asList("status", "achievements");

    private final EtherNyxAuth plugin;

    public AuthCommand(EtherNyxAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.prefix() + ChatColor.YELLOW + "Usage: /auth <status|achievements|info|reset|bypass|ban|unban|banlist|baninfo|reload>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "status":
                return handleStatus(sender);
            case "achievements":
                return handleAchievements(sender, rest);
            case "info":
                return requireAdmin(sender) && handleInfo(sender, rest);
            case "reset":
                return requireAdmin(sender) && handleReset(sender, rest);
            case "bypass":
                return requireAdmin(sender) && handleBypass(sender, rest, true);
            case "unbypass":
                return requireAdmin(sender) && handleBypass(sender, rest, false);
            case "ban":
                return requireAdmin(sender) && handleBan(sender, rest);
            case "tempban":
                return requireAdmin(sender) && handleTempBan(sender, rest);
            case "unban":
                return requireAdmin(sender) && handleUnban(sender, rest);
            case "banlist":
                return requireAdmin(sender) && handleBanList(sender);
            case "baninfo":
                return requireAdmin(sender) && handleBanInfo(sender, rest);
            case "reload":
                return requireAdmin(sender) && handleReload(sender);
            default:
                sender.sendMessage(plugin.prefix() + ChatColor.RED + "Unknown subcommand.");
                return true;
        }
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("ethernyx.auth.admin")) {
            sender.sendMessage(plugin.prefix() + ChatColor.RED + "You do not have permission to do that.");
            return false;
        }
        return true;
    }

    // ---- Public subcommands ----

    private boolean handleStatus(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Console is always considered authenticated.");
            return true;
        }
        Player player = (Player) sender;
        boolean loggedIn = plugin.getAuthManager().isLoggedIn(player.getUniqueId());
        sender.sendMessage(plugin.prefix() + ChatColor.GRAY + "Status: "
                + (loggedIn ? ChatColor.GREEN + "Logged in" : ChatColor.RED + "Not logged in"));
        return true;
    }

    private boolean handleAchievements(CommandSender sender, String[] args) {
        OfflinePlayer target;
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.prefix() + ChatColor.RED + "Console must specify a player.");
                return true;
            }
            target = (Player) sender;
        } else {
            if (!requireAdmin(sender)) return true;
            target = Bukkit.getOfflinePlayer(args[0]);
        }

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(plugin.prefix() + ChatColor.RED + "No data found for that player.");
            return true;
        }

        sender.sendMessage(plugin.prefix() + ChatColor.LIGHT_PURPLE + "Achievements for " + target.getName()
                + ": " + data.getAchievements().size() + "/" + plugin.getAchievementManager().allKeys().length);

        for (String key : plugin.getAchievementManager().allKeys()) {
            boolean earned = data.getAchievements().contains(key);
            String name = plugin.getAchievementManager().getDisplayName(key);
            String desc = plugin.getAchievementManager().getDescription(key);
            ChatColor color = earned ? ChatColor.GREEN : ChatColor.DARK_GRAY;
            sender.sendMessage(color + (earned ? "✔ " : "✘ ") + name + ChatColor.GRAY + " - " + desc);
        }
        return true;
    }

    // ---- Admin: info book ----

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.prefix() + ChatColor.RED + "Only players can receive the info book.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(plugin.prefix() + ChatColor.RED + "Usage: /auth info <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(plugin.prefix() + ChatColor.RED + "No data found for that player.");
            return true;
        }

        Player admin = (Player) sender;
        ItemStack book = plugin.getBookManager().buildInfoBook(target, data);
        admin.getInventory().addItem(book);
        admin.sendMessage(plugin.prefix() + ChatColor.GREEN + "Info book for " + target.getName() + " added to your inventory.");
        return true;
    }

    // ---- Admin: reset password ----

    private boolean handleReset(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(plugin.prefix() + ChatColor.RED + "Usage: /auth reset <player> [newpass]");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        if (data == null || !data.isRegistered()) {
            sender.sendMessage(plugin.prefix() + ChatColor.RED + "That player is not registered.");
            return true;
        }

        if (args.length == 2) {
            String newPass = args[1];
            String validationError = PasswordUtil.validate(newPass, plugin.getConfig());
            if (validationError != null) {
                sender.sendMessage(plugin.prefix() + ChatColor.RED + validationError);
                return true;
            }
            int strength = plugin.getConfig().getInt("authentication.bcrypt-strength", 12);
            data.setPasswordHash(PasswordUtil.hash(newPass, strength));
            sender.sendMessage(plugin.prefix() + ChatColor.GREEN + "Password set for " + target.getName() + ".");
        } else {
            data.setRegistered(false);
            data.setPasswordHash(null);
            sender.sendMessage(plugin.prefix() + ChatColor.GREEN + target.getName()
                    + "'s account has been reset. They must /register again.");
        }

        plugin.getPlayerDataManager().save(target.getUniqueId());
        return true;
    }

    // ---- Admin: bypass ----

    private boolean handleBypass(CommandSender sender, String[] args, boolean enable) {
        if (args.length != 1) {
            sender.sendMessage(plugin.prefix() + ChatColor.RED + "Usage: /auth " + (enable ? "bypass" : "unbypass") + " <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        plugin.getAuthManager().setBypass(target.getUniqueId(), enable);

        Player online = target.getPlayer();
        if (enable && online != null) {
            plugin.getAuthManager().markLoggedIn(online);
            com.ethernyx.auth.listeners.AuthListener.removeLoginRestrictions0(online);
        }

        sender.sendMessage(plugin.prefix() + ChatColor.GREEN + "Bypass " + (enable ? "enabled" : "disabled")
                + " for " + target.getName() + ".");
        return true;
    }

    // ---- Admin: ban / tempban / unban / banlist / baninfo ----

    private boolean handleBan(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.prefix() + ChatColor.RED + "Usage: /auth ban <player> <duration|permanent> <reason>");
            return true;
        }
        return doBan(sender, args, false);
    }

    private boolean handleTempBan(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.prefix() + ChatColor.RED + "Usage: /auth tempban <player> <duration> <reason>");
            return true;
        }
        if (args[1].equalsIgnoreCase("permanent")) {
            sender.sendMessage(plugin.prefix() + ChatColor.RED + "Use /auth ban for permanent bans.");
            return true;
        }
        return doBan(sender, args, true);
    }

    private boolean doBan(CommandSender sender, String[] args, boolean temp) {
        String playerName = args[0];
        String duration = args[1];
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        if (!DurationUtil.isValid(duration)) {
            sender.sendMessage(plugin.prefix() + ChatColor.RED
                    + "Invalid duration. Use formats like 1h, 1d, 1w, 1mo, 1y, or 'permanent'.");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        String moderatorName = sender.getName();
        UUID moderatorUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;

        BanEntry entry = plugin.getBanManager().ban(target.getUniqueId(), duration, reason, moderatorName, moderatorUuid, "EtherNyxAuth");

        sender.sendMessage(plugin.prefix() + ChatColor.GREEN + "Banned " + target.getName()
                + " (" + (entry.isPermanent() ? "Permanent" : duration) + ") for: " + reason);

        plugin.getDiscordManager().announceBan(target.getUniqueId(), entry.isPermanent() ? "Permanent" : duration, reason, moderatorName, temp);
        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(plugin.prefix() + ChatColor.RED + "Usage: /auth unban <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        boolean unbanned = plugin.getBanManager().unban(target.getUniqueId(), sender.getName());

        if (!unbanned) {
            sender.sendMessage(plugin.prefix() + ChatColor.RED + target.getName() + " is not currently banned.");
            return true;
        }

        sender.sendMessage(plugin.prefix() + ChatColor.GREEN + "Unbanned " + target.getName() + ".");
        plugin.getDiscordManager().announceUnban(target.getUniqueId(), sender.getName());
        return true;
    }

    private boolean handleBanList(CommandSender sender) {
        Collection<BanEntry> bans = plugin.getBanManager().getAllBans();
        if (bans.isEmpty()) {
            sender.sendMessage(plugin.prefix() + ChatColor.GRAY + "No players are currently banned.");
            return true;
        }

        sender.sendMessage(plugin.prefix() + ChatColor.LIGHT_PURPLE + "Banned players (" + bans.size() + "):");
        for (BanEntry entry : bans) {
            String name = Bukkit.getOfflinePlayer(entry.getUuid()).getName();
            String remaining = entry.isPermanent() ? "Permanent" : DurationUtil.format(entry.getRemainingMillis());
            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + name
                    + ChatColor.GRAY + " | " + remaining + " | " + entry.getReason());
        }
        return true;
    }

    private boolean handleBanInfo(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(plugin.prefix() + ChatColor.RED + "Usage: /auth baninfo <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        BanEntry entry = plugin.getBanManager().getBan(target.getUniqueId());

        if (entry == null) {
            sender.sendMessage(plugin.prefix() + ChatColor.GRAY + target.getName() + " is not currently banned.");
            return true;
        }

        sender.sendMessage(plugin.prefix() + ChatColor.LIGHT_PURPLE + "Ban info for " + target.getName() + ":");
        sender.sendMessage(ChatColor.GRAY + "Reason: " + ChatColor.WHITE + entry.getReason());
        sender.sendMessage(ChatColor.GRAY + "Moderator: " + ChatColor.WHITE + entry.getModerator());
        sender.sendMessage(ChatColor.GRAY + "Duration: " + ChatColor.WHITE + (entry.isPermanent() ? "Permanent" : entry.getDuration()));
        sender.sendMessage(ChatColor.GRAY + "Remaining: " + ChatColor.WHITE
                + (entry.isPermanent() ? "Permanent" : DurationUtil.format(entry.getRemainingMillis())));
        sender.sendMessage(ChatColor.GRAY + "Source: " + ChatColor.WHITE + entry.getSource());
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(plugin.prefix() + ChatColor.GREEN + "Configuration reloaded.");
        return true;
    }

    // ---- Tab completion ----

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(PUBLIC_SUBCOMMANDS);
            if (sender.hasPermission("ethernyx.auth.admin")) {
                options.addAll(ADMIN_SUBCOMMANDS);
            }
            return filter(options, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (Arrays.asList("info", "reset", "bypass", "unbypass", "ban", "tempban", "unban", "baninfo", "achievements").contains(sub)) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("ban") || sub.equals("tempban")) {
                return filter(Arrays.asList("1h", "1d", "1w", "1mo", "1y", "permanent"), args[2]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
