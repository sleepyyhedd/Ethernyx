package com.ethernyx.auth.managers;

import com.ethernyx.auth.EtherNyxAuth;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks live authentication session state for online players.
 * All maps are ConcurrentHashMap-backed to be safe across the main thread
 * and any async tasks that touch them (e.g. scheduled kicks).
 */
public class AuthManager {

    private final EtherNyxAuth plugin;

    private final Set<UUID> loggedIn = ConcurrentHashMap.newKeySet();
    private final Set<UUID> bypassed = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingIpConfirmation = ConcurrentHashMap.newKeySet();

    private final ConcurrentHashMap<UUID, AtomicInteger> failedAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastAttemptMillis = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BukkitTask> kickTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> sessionStartMillis = new ConcurrentHashMap<>();

    public AuthManager(EtherNyxAuth plugin) {
        this.plugin = plugin;
    }

    // ---- Login state ----

    public boolean isLoggedIn(UUID uuid) {
        return loggedIn.contains(uuid) || bypassed.contains(uuid);
    }

    public void markLoggedIn(Player player) {
        UUID uuid = player.getUniqueId();
        loggedIn.add(uuid);
        sessionStartMillis.put(uuid, System.currentTimeMillis());
        cancelKickTimer(uuid);
        resetAttempts(uuid);
    }

    public void markLoggedOut(UUID uuid) {
        loggedIn.remove(uuid);
        pendingIpConfirmation.remove(uuid);
        cancelKickTimer(uuid);
        resetAttempts(uuid);
        Long start = sessionStartMillis.remove(uuid);
        if (start != null) {
            long elapsedSeconds = (System.currentTimeMillis() - start) / 1000L;
            var data = plugin.getPlayerDataManager().get(uuid);
            if (data != null) {
                data.addPlaytimeSeconds(elapsedSeconds);
            }
        }
    }

    public void setBypass(UUID uuid, boolean bypass) {
        if (bypass) {
            bypassed.add(uuid);
        } else {
            bypassed.remove(uuid);
        }
    }

    public boolean hasBypass(UUID uuid) {
        return bypassed.contains(uuid);
    }

    public void setPendingIpConfirmation(UUID uuid, boolean pending) {
        if (pending) {
            pendingIpConfirmation.add(uuid);
        } else {
            pendingIpConfirmation.remove(uuid);
        }
    }

    public boolean isPendingIpConfirmation(UUID uuid) {
        return pendingIpConfirmation.contains(uuid);
    }

    // ---- Brute force protection ----

    public int registerFailedAttempt(UUID uuid) {
        AtomicInteger count = failedAttempts.computeIfAbsent(uuid, k -> new AtomicInteger(0));
        lastAttemptMillis.put(uuid, System.currentTimeMillis());
        return count.incrementAndGet();
    }

    public int getFailedAttempts(UUID uuid) {
        AtomicInteger count = failedAttempts.get(uuid);
        return count != null ? count.get() : 0;
    }

    public void resetAttempts(UUID uuid) {
        failedAttempts.remove(uuid);
        lastAttemptMillis.remove(uuid);
    }

    public boolean isOnCooldown(UUID uuid) {
        Long last = lastAttemptMillis.get(uuid);
        if (last == null) return false;
        int cooldownSeconds = plugin.getConfig().getInt("authentication.attempt-cooldown", 3);
        return (System.currentTimeMillis() - last) < (cooldownSeconds * 1000L);
    }

    public long getCooldownRemainingMillis(UUID uuid) {
        Long last = lastAttemptMillis.get(uuid);
        if (last == null) return 0;
        int cooldownSeconds = plugin.getConfig().getInt("authentication.attempt-cooldown", 3);
        long remaining = (cooldownSeconds * 1000L) - (System.currentTimeMillis() - last);
        return Math.max(0, remaining);
    }

    // ---- Auto-kick timer ----

    public void startKickTimer(Player player) {
        cancelKickTimer(player.getUniqueId());
        int timeoutSeconds = plugin.getConfig().getInt("authentication.login-timeout", 60);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !isLoggedIn(player.getUniqueId())) {
                player.kickPlayer(plugin.prefix() + org.bukkit.ChatColor.RED
                        + "You took too long to log in.");
            }
        }, timeoutSeconds * 20L);

        kickTimers.put(player.getUniqueId(), task);
    }

    public void cancelKickTimer(UUID uuid) {
        BukkitTask task = kickTimers.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    // ---- Cleanup ----

    public void cleanup(UUID uuid) {
        markLoggedOut(uuid);
        bypassed.remove(uuid);
    }

    public void cleanupAll() {
        for (UUID uuid : kickTimers.keySet()) {
            cancelKickTimer(uuid);
        }
        loggedIn.clear();
        pendingIpConfirmation.clear();
        failedAttempts.clear();
        lastAttemptMillis.clear();
        sessionStartMillis.clear();
    }
}
