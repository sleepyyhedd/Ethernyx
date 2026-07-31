package com.ethernyx.auth.listeners;

import com.ethernyx.auth.EtherNyxAuth;
import com.ethernyx.auth.managers.PlayerDataManager;
import com.ethernyx.auth.models.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * Automatically authenticates Bedrock players connecting through Floodgate,
 * since they're already authenticated via Xbox Live and don't need a
 * separate in-game password.
 */
public class FloodgateListener implements Listener {

    private final EtherNyxAuth plugin;

    public FloodgateListener(EtherNyxAuth plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        boolean isBedrockPlayer;
        try {
            isBedrockPlayer = FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            // Floodgate API not fully initialized or unavailable; skip auto-auth.
            return;
        }

        if (!isBedrockPlayer) return;

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        if (!data.isRegistered()) {
            data.setRegistered(true);
            data.setRegisteredDate(PlayerDataManager.nowIso());
        }

        data.setIp(AuthListener.getIp(player));
        data.setLastLogin(PlayerDataManager.nowIso());
        data.addLogin(PlayerDataManager.nowIso() + ": Joined (Bedrock auto-auth)");

        plugin.getAuthManager().markLoggedIn(player);
        player.sendMessage(plugin.prefix() + ChatColor.GREEN + "Welcome! You've been automatically authenticated via Bedrock.");
    }
}
