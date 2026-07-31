package com.ethernyx.auth.listeners;

import com.ethernyx.auth.EtherNyxAuth;
import com.ethernyx.auth.models.BanEntry;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerKickEvent;

/**
 * Enforces EtherNyxAuth's own bans on login, and detects when another plugin
 * kicks/bans a player for ban-like reasons so it can be logged and announced
 * to Discord consistently (per the "Third-Party Detection" requirement).
 *
 * True hook-level integration with specific plugins like GriefPrevention or
 * an "Anti-Spam" plugin would normally use their APIs directly; since those
 * vary widely, this listener also offers a generic fallback that inspects
 * PlayerKickEvent reasons for ban-like language from other plugins.
 */
public class BanListener implements Listener {

    private final EtherNyxAuth plugin;

    public BanListener(EtherNyxAuth plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        if (!plugin.getConfig().getBoolean("ban.enabled", true)) return;

        java.util.UUID uuid = event.getPlayer().getUniqueId();
        if (!plugin.getBanManager().isBanned(uuid)) return;

        BanEntry ban = plugin.getBanManager().getBan(uuid);
        if (ban == null) return; // Expired and auto-unbanned between check and here.

        String message = plugin.getBanManager().buildKickMessage(ban);
        event.disallow(PlayerLoginEvent.Result.KICK_BANNED, message);
    }

    /**
     * Detects kicks issued by other plugins that look like bans (based on
     * common phrasing) and logs/announces them via the ban manager so they
     * appear consistently in ban history and Discord, per the third-party
     * detection requirement. This is a best-effort heuristic fallback for
     * plugins without a dedicated integration hook.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        if (!plugin.getConfig().getBoolean("ban.enabled", true)) return;

        String reason = ChatColor.stripColor(event.getReason());
        if (reason == null) return;

        String lower = reason.toLowerCase();
        boolean looksLikeBan = lower.contains("banned") || lower.contains("ban reason")
                || lower.contains("you have been banned");

        if (!looksLikeBan) return;

        java.util.UUID uuid = event.getPlayer().getUniqueId();

        // Avoid double-logging bans that EtherNyxAuth itself just issued.
        if (plugin.getBanManager().isBanned(uuid)) return;

        String sourcePlugin = detectSourcePlugin();
        plugin.getBanManager().recordThirdPartyBan(uuid, reason, sourcePlugin);
    }

    private String detectSourcePlugin() {
        // Best-effort: check which known moderation plugins are enabled.
        String[] known = {"GriefPrevention", "AdvancedBan", "LiteBans", "BanManager", "AntiSpam"};
        for (String name : known) {
            if (plugin.getServer().getPluginManager().isPluginEnabled(name)) {
                return name;
            }
        }
        return "Unknown Plugin";
    }
}
