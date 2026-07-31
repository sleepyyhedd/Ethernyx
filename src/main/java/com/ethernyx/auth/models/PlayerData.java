package com.ethernyx.auth.models;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds all persisted data for a single player.
 * Access to mutable collections is synchronized externally by PlayerDataManager.
 */
public class PlayerData {

    private final UUID uuid;

    // Account info
    private String passwordHash;
    private String ip;
    private int port;
    private String deviceFingerprint;
    private boolean registered;
    private String registeredDate;
    private String lastLogin;
    private boolean rememberMe;
    private String rememberIp;
    private long playtimeSeconds;

    // Stats
    private int kills;
    private int deaths;
    private int mobsKilled;
    private long blocksMined;
    private long blocksPlaced;
    private long itemsCrafted;
    private double distanceWalked;
    private int diamondsMined;
    private long itemsCollected;
    private boolean enteredNether;
    private boolean enteredEnd;
    private int witherKills;
    private int dragonKills;

    // Achievements
    private final List<String> achievements = new CopyOnWriteArrayList<>();

    // History (bounded lists, most-recent-first)
    private final List<String> deathHistory = new CopyOnWriteArrayList<>();
    private final List<String> killHistory = new CopyOnWriteArrayList<>();
    private final List<String> loginHistory = new CopyOnWriteArrayList<>();

    private static final int MAX_HISTORY_ENTRIES = 20;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public void addDeath(String entry) {
        addBounded(deathHistory, entry);
    }

    public void addKill(String entry) {
        addBounded(killHistory, entry);
    }

    public void addLogin(String entry) {
        addBounded(loginHistory, entry);
    }

    private void addBounded(List<String> list, String entry) {
        list.add(0, entry);
        while (list.size() > MAX_HISTORY_ENTRIES) {
            list.remove(list.size() - 1);
        }
    }

    // ---- Getters / setters ----

    public UUID getUuid() { return uuid; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }

    public boolean isRegistered() { return registered; }
    public void setRegistered(boolean registered) { this.registered = registered; }

    public String getRegisteredDate() { return registeredDate; }
    public void setRegisteredDate(String registeredDate) { this.registeredDate = registeredDate; }

    public String getLastLogin() { return lastLogin; }
    public void setLastLogin(String lastLogin) { this.lastLogin = lastLogin; }

    public boolean isRememberMe() { return rememberMe; }
    public void setRememberMe(boolean rememberMe) { this.rememberMe = rememberMe; }

    public String getRememberIp() { return rememberIp; }
    public void setRememberIp(String rememberIp) { this.rememberIp = rememberIp; }

    public long getPlaytimeSeconds() { return playtimeSeconds; }
    public void setPlaytimeSeconds(long playtimeSeconds) { this.playtimeSeconds = playtimeSeconds; }
    public void addPlaytimeSeconds(long seconds) { this.playtimeSeconds += seconds; }

    public int getKills() { return kills; }
    public void setKills(int kills) { this.kills = kills; }
    public void incrementKills() { this.kills++; }

    public int getDeaths() { return deaths; }
    public void setDeaths(int deaths) { this.deaths = deaths; }
    public void incrementDeaths() { this.deaths++; }

    public int getMobsKilled() { return mobsKilled; }
    public void setMobsKilled(int mobsKilled) { this.mobsKilled = mobsKilled; }
    public void incrementMobsKilled() { this.mobsKilled++; }

    public long getBlocksMined() { return blocksMined; }
    public void setBlocksMined(long blocksMined) { this.blocksMined = blocksMined; }
    public void incrementBlocksMined() { this.blocksMined++; }

    public long getBlocksPlaced() { return blocksPlaced; }
    public void setBlocksPlaced(long blocksPlaced) { this.blocksPlaced = blocksPlaced; }
    public void incrementBlocksPlaced() { this.blocksPlaced++; }

    public long getItemsCrafted() { return itemsCrafted; }
    public void setItemsCrafted(long itemsCrafted) { this.itemsCrafted = itemsCrafted; }
    public void incrementItemsCrafted(int amount) { this.itemsCrafted += amount; }

    public double getDistanceWalked() { return distanceWalked; }
    public void setDistanceWalked(double distanceWalked) { this.distanceWalked = distanceWalked; }
    public void addDistanceWalked(double distance) { this.distanceWalked += distance; }

    public int getDiamondsMined() { return diamondsMined; }
    public void setDiamondsMined(int diamondsMined) { this.diamondsMined = diamondsMined; }
    public void incrementDiamondsMined() { this.diamondsMined++; }

    public long getItemsCollected() { return itemsCollected; }
    public void setItemsCollected(long itemsCollected) { this.itemsCollected = itemsCollected; }
    public void addItemsCollected(int amount) { this.itemsCollected += amount; }

    public boolean hasEnteredNether() { return enteredNether; }
    public void setEnteredNether(boolean enteredNether) { this.enteredNether = enteredNether; }

    public boolean hasEnteredEnd() { return enteredEnd; }
    public void setEnteredEnd(boolean enteredEnd) { this.enteredEnd = enteredEnd; }

    public int getWitherKills() { return witherKills; }
    public void incrementWitherKills() { this.witherKills++; }

    public int getDragonKills() { return dragonKills; }
    public void incrementDragonKills() { this.dragonKills++; }

    public List<String> getAchievements() { return achievements; }

    public List<String> getDeathHistory() { return deathHistory; }
    public List<String> getKillHistory() { return killHistory; }
    public List<String> getLoginHistory() { return loginHistory; }

    public double getKdRatio() {
        if (deaths == 0) return kills;
        return Math.round((kills / (double) deaths) * 100.0) / 100.0;
    }
}
