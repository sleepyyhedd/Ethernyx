package com.ethernyx.auth.managers;

import com.ethernyx.auth.EtherNyxAuth;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Sends moderation announcements to Discord via DiscordSRV if present.
 * DiscordSRV is a soft-dependency; all interaction goes through reflection
 * so the plugin still loads fine on servers without it (falling back to
 * console logging only).
 */
public class DiscordManager {

    private final EtherNyxAuth plugin;
    private final boolean discordSrvAvailable;

    public DiscordManager(EtherNyxAuth plugin) {
        this.plugin = plugin;
        this.discordSrvAvailable = Bukkit.getPluginManager().isPluginEnabled("DiscordSRV");
    }

    private String playerName(UUID uuid) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        return p.getName() != null ? p.getName() : uuid.toString();
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    public void announceBan(UUID target, String duration, String reason, String moderator, boolean temp) {
        String templateKey = temp ? "discord.messages.tempban" : "discord.messages.ban";
        String template = plugin.getConfig().getString(templateKey, "");
        String message = template
                .replace("{player}", playerName(target))
                .replace("{duration}", duration)
                .replace("{reason}", reason)
                .replace("{moderator}", moderator)
                .replace("{date}", now());
        send(message);
    }

    public void announceUnban(UUID target, String moderator) {
        String template = plugin.getConfig().getString("discord.messages.unban", "");
        String message = template
                .replace("{player}", playerName(target))
                .replace("{moderator}", moderator)
                .replace("{date}", now());
        send(message);
    }

    public void announceKick(UUID target, String reason, String moderator) {
        String template = plugin.getConfig().getString("discord.messages.kick", "");
        String message = template
                .replace("{player}", playerName(target))
                .replace("{reason}", reason)
                .replace("{moderator}", moderator);
        send(message);
    }

    public void announceThirdPartyBan(UUID target, String sourcePlugin, String reason) {
        String template = plugin.getConfig().getString("discord.messages.third-party-ban", "");
        String message = template
                .replace("{player}", playerName(target))
                .replace("{source}", sourcePlugin)
                .replace("{reason}", reason != null ? reason : "No reason given")
                .replace("{date}", now());
        send(message);
    }

    /**
     * Sends a message to the configured mod-logs channel.
     * Uses reflection against DiscordSRV's API so this plugin can compile
     * and run without DiscordSRV present. If unavailable, falls back to
     * the webhook (if configured) or console logging.
     */
    private void send(String message) {
        if (!plugin.getConfig().getBoolean("discord.enabled", true)) return;

        String channelId = plugin.getConfig().getString("discord.channels.mod-logs", "");

        if (discordSrvAvailable) {
            try {
                Class<?> discordSrvClass = Class.forName("github.scarsz.discordsrv.DiscordSRV");
                Object instance = discordSrvClass.getMethod("getPlugin").invoke(null);
                Object jda = discordSrvClass.getMethod("getJda").invoke(instance);

                Class<?> jdaClass = Class.forName("net.dv8tion.jda.api.JDA");
                Object textChannel = jdaClass.getMethod("getTextChannelById", String.class).invoke(jda, channelId);

                if (textChannel != null) {
                    Class<?> textChannelClass = textChannel.getClass();
                    Object messageAction = textChannelClass.getMethod("sendMessage", CharSequence.class)
                            .invoke(textChannel, message);
                    messageAction.getClass().getMethod("queue").invoke(messageAction);
                    return;
                }
            } catch (Exception e) {
                if (plugin.getConfig().getBoolean("settings.debug", false)) {
                    plugin.getLogger().warning("DiscordSRV reflection call failed: " + e.getMessage());
                }
                // Fall through to webhook/console fallback below.
            }
        }

        if (plugin.getConfig().getBoolean("discord.webhook.enabled", false)) {
            sendViaWebhook(message);
        } else {
            plugin.getLogger().info("[Discord] " + message.replace("\n", " | "));
        }
    }

    private void sendViaWebhook(String message) {
        String url = plugin.getConfig().getString("discord.webhook.url", "");
        if (url == null || url.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String username = plugin.getConfig().getString("discord.webhook.username", "EtherNyx Moderation");
                String avatarUrl = plugin.getConfig().getString("discord.webhook.avatar-url", "");

                String jsonPayload = "{"
                        + "\"username\":\"" + escapeJson(username) + "\","
                        + (avatarUrl.isEmpty() ? "" : "\"avatar_url\":\"" + escapeJson(avatarUrl) + "\",")
                        + "\"content\":\"" + escapeJson(message) + "\""
                        + "}";

                java.net.URL webhookUrl = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) webhookUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                conn.getResponseCode(); // trigger send
                conn.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
            }
        });
    }

    private String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
