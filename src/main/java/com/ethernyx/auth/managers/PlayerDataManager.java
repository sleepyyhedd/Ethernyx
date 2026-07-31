package com.ethernyx.auth.managers;

import com.ethernyx.auth.EtherNyxAuth;
import com.ethernyx.auth.models.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe manager for player data. Uses a ConcurrentHashMap for in-memory
 * cache and synchronizes file I/O to avoid concurrent modification issues.
 */
public class PlayerDataManager {

    private static final SimpleDateFormat ISO_FORMAT;
    static {
        ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    }

    private final EtherNyxAuth plugin;
    private final File file;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Object fileLock = new Object();

    public PlayerDataManager(EtherNyxAuth plugin) {
        this.plugin = plugin;
        String fileName = plugin.getConfig().getString("database.yaml.players-file", "players.yml");
        this.file = new File(plugin.getDataFolder(), fileName);
    }

    public static String nowIso() {
        synchronized (ISO_FORMAT) {
            return ISO_FORMAT.format(new Date());
        }
    }

    public static String nowIsoFrom(long epochMillis) {
        if (epochMillis <= 0) return nowIso();
        synchronized (ISO_FORMAT) {
            return ISO_FORMAT.format(new Date(epochMillis));
        }
    }

    public void load() {
        synchronized (fileLock) {
            if (!file.exists()) {
                try {
                    plugin.getDataFolder().mkdirs();
                    file.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not create players.yml: " + e.getMessage());
                }
                return;
            }

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection playersSection = yaml.getConfigurationSection("players");
            if (playersSection == null) return;

            for (String uuidStr : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection sec = playersSection.getConfigurationSection(uuidStr);
                    PlayerData data = deserialize(uuid, sec);
                    cache.put(uuid, data);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Skipping invalid UUID in players.yml: " + uuidStr);
                }
            }
            plugin.getLogger().info("Loaded " + cache.size() + " player records.");
        }
    }

    public void saveAll() {
        synchronized (fileLock) {
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, PlayerData> entry : cache.entrySet()) {
                serialize(yaml, entry.getKey(), entry.getValue());
            }
            backupIfConfigured();
            try {
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save players.yml: " + e.getMessage());
            }
        }
    }

    private void backupIfConfigured() {
        if (!plugin.getConfig().getBoolean("database.yaml.backup-on-save", true)) return;
        if (!file.exists()) return;

        String backupDirName = plugin.getConfig().getString("database.yaml.backup-directory", "backups/");
        File backupDir = new File(plugin.getDataFolder(), backupDirName);
        if (!backupDir.exists()) backupDir.mkdirs();

        File backupFile = new File(backupDir, "players-" + System.currentTimeMillis() + ".yml");
        try {
            java.nio.file.Files.copy(file.toPath(), backupFile.toPath());
        } catch (IOException ignored) {
            // Non-fatal
        }

        int maxBackups = plugin.getConfig().getInt("database.yaml.max-backups", 10);
        File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("players-"));
        if (backups != null && backups.length > maxBackups) {
            java.util.Arrays.sort(backups, java.util.Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < backups.length - maxBackups; i++) {
                backups[i].delete();
            }
        }
    }

    private void serialize(YamlConfiguration yaml, UUID uuid, PlayerData data) {
        String base = "players." + uuid;
        yaml.set(base + ".password", data.getPasswordHash());
        yaml.set(base + ".ip", data.getIp());
        yaml.set(base + ".port", data.getPort());
        yaml.set(base + ".device-fingerprint", data.getDeviceFingerprint());
        yaml.set(base + ".registered", data.isRegistered());
        yaml.set(base + ".registered-date", data.getRegisteredDate());
        yaml.set(base + ".last-login", data.getLastLogin());
        yaml.set(base + ".remember-me", data.isRememberMe());
        yaml.set(base + ".remember-ip", data.getRememberIp());
        yaml.set(base + ".playtime-seconds", data.getPlaytimeSeconds());

        yaml.set(base + ".stats.kills", data.getKills());
        yaml.set(base + ".stats.deaths", data.getDeaths());
        yaml.set(base + ".stats.mobs-killed", data.getMobsKilled());
        yaml.set(base + ".stats.blocks-mined", data.getBlocksMined());
        yaml.set(base + ".stats.blocks-placed", data.getBlocksPlaced());
        yaml.set(base + ".stats.items-crafted", data.getItemsCrafted());
        yaml.set(base + ".stats.distance-walked", data.getDistanceWalked());
        yaml.set(base + ".stats.diamonds-mined", data.getDiamondsMined());
        yaml.set(base + ".stats.items-collected", data.getItemsCollected());
        yaml.set(base + ".stats.entered-nether", data.hasEnteredNether());
        yaml.set(base + ".stats.entered-end", data.hasEnteredEnd());
        yaml.set(base + ".stats.wither-kills", data.getWitherKills());
        yaml.set(base + ".stats.dragon-kills", data.getDragonKills());

        yaml.set(base + ".achievements", data.getAchievements());

        yaml.set(base + ".history.deaths", data.getDeathHistory());
        yaml.set(base + ".history.kills", data.getKillHistory());
        yaml.set(base + ".history.logins", data.getLoginHistory());
    }

    @SuppressWarnings("unchecked")
    private PlayerData deserialize(UUID uuid, ConfigurationSection sec) {
        PlayerData data = new PlayerData(uuid);
        if (sec == null) return data;

        data.setPasswordHash(sec.getString("password"));
        data.setIp(sec.getString("ip"));
        data.setPort(sec.getInt("port"));
        data.setDeviceFingerprint(sec.getString("device-fingerprint"));
        data.setRegistered(sec.getBoolean("registered"));
        data.setRegisteredDate(sec.getString("registered-date"));
        data.setLastLogin(sec.getString("last-login"));
        data.setRememberMe(sec.getBoolean("remember-me"));
        data.setRememberIp(sec.getString("remember-ip"));
        data.setPlaytimeSeconds(sec.getLong("playtime-seconds"));

        data.setKills(sec.getInt("stats.kills"));
        data.setDeaths(sec.getInt("stats.deaths"));
        data.setMobsKilled(sec.getInt("stats.mobs-killed"));
        data.setBlocksMined(sec.getLong("stats.blocks-mined"));
        data.setBlocksPlaced(sec.getLong("stats.blocks-placed"));
        data.setItemsCrafted(sec.getLong("stats.items-crafted"));
        data.setDistanceWalked(sec.getDouble("stats.distance-walked"));
        data.setDiamondsMined(sec.getInt("stats.diamonds-mined"));
        data.setItemsCollected(sec.getLong("stats.items-collected"));
        data.setEnteredNether(sec.getBoolean("stats.entered-nether"));
        data.setEnteredEnd(sec.getBoolean("stats.entered-end"));

        List<String> achievements = sec.getStringList("achievements");
        data.getAchievements().addAll(achievements);

        data.getDeathHistory().addAll(sec.getStringList("history.deaths"));
        data.getKillHistory().addAll(sec.getStringList("history.kills"));
        data.getLoginHistory().addAll(sec.getStringList("history.logins"));

        return data;
    }

    // ---- Public API ----

    public PlayerData getOrCreate(UUID uuid) {
        return cache.computeIfAbsent(uuid, PlayerData::new);
    }

    public PlayerData get(UUID uuid) {
        return cache.get(uuid);
    }

    public boolean exists(UUID uuid) {
        PlayerData data = cache.get(uuid);
        return data != null && data.isRegistered();
    }

    public void save(UUID uuid) {
        // For simplicity and to avoid partial-file races, delegate to saveAll().
        // Kept as its own method so call sites express intent clearly.
        saveAll();
    }

    public UUID findUuidByName(String playerName) {
        org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(playerName);
        if (offline != null && offline.getUniqueId() != null) {
            return offline.getUniqueId();
        }
        return null;
    }
}
