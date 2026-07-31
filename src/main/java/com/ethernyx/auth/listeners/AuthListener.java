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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.net.InetSocketAddress;

public class AuthListener implements Listener {

    private final EtherNyxAuth plugin;

    public AuthListener(EtherNyxAuth plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.getAuthManager().hasBypass(player.getUniqueId())
                || player.hasPermission("ethernyx.auth.bypass")) {
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        String currentIp = getIp(player);

        boolean autoLoggedIn = false;

        if (data.isRegistered() && data.isRememberMe()
                && plugin.getConfig().getBoolean("authentication.remember-me-enabled", true)) {

            if (currentIp != null && currentIp.equals(data.getRememberIp()) && !isRememberExpired(data)) {
                plugin.getAuthManager().markLoggedIn(player);
                data.setLastLogin(PlayerDataManager.nowIso());
                data.addLogin(PlayerDataManager.nowIso() + ": Auto-login (remembered device)");
                player.sendMessage(plugin.prefix() + ChatColor.GREEN + "Welcome back! You have been automatically logged in.");
                autoLoggedIn = true;
            }
        }

        if (!autoLoggedIn) {
            applyLoginRestrictions(player);

            if (!data.isRegistered()) {
                player.sendMessage(plugin.prefix() + ChatColor.YELLOW
                        + "You are not registered. Use /register <password> <confirm>");
            } else {
                boolean ipChanged = currentIp != null && data.getIp() != null && !currentIp.equals(data.getIp());
                if (ipChanged && plugin.getConfig().getBoolean("security.device.prompt-on-ip-change", true)) {
                    plugin.getAuthManager().setPendingIpConfirmation(player.getUniqueId(), true);
                    player.sendMessage(plugin.prefix() + ChatColor.YELLOW
                            + "We noticed a new device or IP. Please log in with /login <password> to confirm it's you.");
                } else {
                    player.sendMessage(plugin.prefix() + ChatColor.YELLOW
                            + "Please log in using /login <password>");
                }
            }

            plugin.getAuthManager().startKickTimer(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getAuthManager().cleanup(player.getUniqueId());
    }

    private boolean isRememberExpired(PlayerData data) {
        // Remember-me duration is enforced at last-login granularity; since we
        // don't store a separate remember-set timestamp, fall back to last-login age.
        try {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            long lastLoginMillis = fmt.parse(data.getLastLogin()).getTime();
            int days = plugin.getConfig().getInt("authentication.remember-me-duration", 30);
            long maxAgeMillis = days * 86_400_000L;
            return (System.currentTimeMillis() - lastLoginMillis) > maxAgeMillis;
        } catch (Exception e) {
            return true; // If we can't parse it, don't trust it.
        }
    }

    public static String getIp(Player player) {
        InetSocketAddress address = player.getAddress();
        return address != null && address.getAddress() != null ? address.getAddress().getHostAddress() : null;
    }

    public void applyLoginRestrictions(Player player) {
        applyLoginRestrictions(plugin, player);
    }

    public static void applyLoginRestrictions(EtherNyxAuth plugin, Player player) {
        if (!plugin.getConfig().getBoolean("security.effects.blindness.enabled", true)) return;

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS,
                plugin.getConfig().getInt("security.effects.blindness.duration", 999999),
                1,
                false,
                plugin.getConfig().getBoolean("security.effects.blindness.show-particles", false),
                plugin.getConfig().getBoolean("security.effects.blindness.show-icon", false)
        ));

        if (plugin.getConfig().getBoolean("security.effects.slowness.enabled", true)) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOW,
                    plugin.getConfig().getInt("security.effects.slowness.duration", 999999),
                    255,
                    false,
                    plugin.getConfig().getBoolean("security.effects.slowness.show-particles", false),
                    plugin.getConfig().getBoolean("security.effects.slowness.show-icon", false)
            ));
        }

        if (plugin.getConfig().getBoolean("security.movement.enabled", true)) {
            player.setWalkSpeed((float) plugin.getConfig().getDouble("security.movement.walk-speed", 0.0));
            player.setFlySpeed((float) plugin.getConfig().getDouble("security.movement.fly-speed", 0.0));
        }

        player.setFoodLevel(plugin.getConfig().getInt("security.health.food-value", 20));
        player.setSaturation((float) plugin.getConfig().getDouble("security.health.saturation-value", 10));
        player.setHealth(Math.min(player.getHealth(), plugin.getConfig().getDouble("security.health.health-value", 20.0)));
    }

    public void removeLoginRestrictions(Player player) {
        removeLoginRestrictions0(player);
    }

    public static void removeLoginRestrictions0(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOW);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
    }
}
