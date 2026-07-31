package com.ethernyx.auth.managers;

import com.ethernyx.auth.EtherNyxAuth;
import com.ethernyx.auth.models.BanEntry;
import com.ethernyx.auth.util.DurationUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BanManager {

    private final EtherNyxAuth plugin;
    private final File file;
    private final Map<UUID, BanEntry> activeBans = new ConcurrentHashMap<>();
    private final Map<UUID, List<Map<String, Object>>> banHistory = new ConcurrentHashMap<>();
    private final Object fileLock = new Object();

    public BanManager(EtherNyxAuth plugin) {
        this.plugin = plugin;
        String fileName = plugin.getConfig().getString("database.yaml.bans-file", "bans.yml");
        this.file = new File(plugin.getDataFolder(), fileName);
    }

    public void load() {
        synchronized (fileLock) {
            if (!file.exists()) {
                try {
                    plugin.getDataFolder().mkdirs();
                    file.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not create bans.yml: " + e.getMessage());
                }
                return;
            }

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

            ConfigurationSection bansSection = yaml.getConfigurationSection("bans");
            if (bansSection != null) {
                for (String uuidStr : bansSection.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        ConfigurationSection sec = bansSection.getConfigurationSection(uuidStr);
                        if (sec == null || !sec.getBoolean("banned", false)) continue;
                        BanEntry entry = new BanEntry(uuid);
                        entry.setReason(sec.getString("reason"));
                        entry.setDuration(sec.getString("duration"));
                        entry.setModerator(sec.getString("moderator"));
                        String modUuid = sec.getString("moderator-uuid");
                        if (modUuid != null && !modUuid.isEmpty()) {
                            try { entry.setModeratorUuid(UUID.fromString(modUuid)); } catch (Exception ignored) {}
                        }
                        entry.setPermanent(sec.getBoolean("permanent", false));
                        entry.setDateEpochMillis(parseIsoOrZero(sec.getString("date")));
                        entry.setExpiresEpochMillis(sec.contains("expires") ? parseIsoOrZero(sec.getString("expires")) : -1);
                        entry.setSource(sec.getString("source", "EtherNyxAuth"));
                        activeBans.put(uuid, entry);
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            ConfigurationSection historySection = yaml.getConfigurationSection("ban-history");
            if (historySection != null) {
                for (String uuidStr : historySection.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        List<Map<?, ?>> rawList = historySection.getMapList(uuidStr);
                        List<Map<String, Object>> list = new ArrayList<>();
                        for (Map<?, ?> m : rawList) {
                            Map<String, Object> converted = new LinkedHashMap<>();
                            for (Map.Entry<?, ?> e : m.entrySet()) {
                                converted.put(String.valueOf(e.getKey()), e.getValue());
                            }
                            list.add(converted);
                        }
                        banHistory.put(uuid, list);
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            plugin.getLogger().info("Loaded " + activeBans.size() + " active bans.");
        }
    }

    public void save() {
        synchronized (fileLock) {
            YamlConfiguration yaml = new YamlConfiguration();

            for (Map.Entry<UUID, BanEntry> entry : activeBans.entrySet()) {
                BanEntry ban = entry.getValue();
                String base = "bans." + entry.getKey();
                yaml.set(base + ".banned", true);
                yaml.set(base + ".reason", ban.getReason());
                yaml.set(base + ".duration", ban.getDuration());
                yaml.set(base + ".moderator", ban.getModerator());
                yaml.set(base + ".moderator-uuid", ban.getModeratorUuid() != null ? ban.getModeratorUuid().toString() : null);
                yaml.set(base + ".date", PlayerDataManager.nowIsoFrom(ban.getDateEpochMillis()));
                if (!ban.isPermanent()) {
                    yaml.set(base + ".expires", PlayerDataManager.nowIsoFrom(ban.getExpiresEpochMillis()));
                }
                yaml.set(base + ".permanent", ban.isPermanent());
                yaml.set(base + ".source", ban.getSource());
            }

            for (Map.Entry<UUID, List<Map<String, Object>>> entry : banHistory.entrySet()) {
                yaml.set("ban-history." + entry.getKey(), entry.getValue());
            }

            try {
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save bans.yml: " + e.getMessage());
            }
        }
    }

    private long parseIsoOrZero(String iso) {
        if (iso == null) return 0;
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(iso).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    // ---- Ban operations ----

    public BanEntry ban(UUID target, String duration, String reason, String moderatorName, UUID moderatorUuid, String source) {
        BanEntry entry = new BanEntry(target);
        entry.setReason(reason);
        entry.setDuration(duration);
        entry.setModerator(moderatorName);
        entry.setModeratorUuid(moderatorUuid);
        entry.setSource(source);
        entry.setDateEpochMillis(System.currentTimeMillis());

        long durationMillis = DurationUtil.parseToMillis(duration);
        if (durationMillis < 0) {
            entry.setPermanent(true);
            entry.setExpiresEpochMillis(-1);
        } else {
            entry.setPermanent(false);
            entry.setExpiresEpochMillis(System.currentTimeMillis() + durationMillis);
        }

        activeBans.put(target, entry);
        recordHistory(target, entry, false, null, null);
        save();

        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            online.kickPlayer(buildKickMessage(entry));
        }

        return entry;
    }

    public boolean unban(UUID target, String unbannedBy) {
        BanEntry removed = activeBans.remove(target);
        if (removed == null) return false;

        List<Map<String, Object>> history = banHistory.get(target);
        if (history != null && !history.isEmpty()) {
            Map<String, Object> last = history.get(history.size() - 1);
            last.put("unbanned", true);
            last.put("unbanned-date", PlayerDataManager.nowIso());
            last.put("unbanned-by", unbannedBy);
        }

        save();
        return true;
    }

    private void recordHistory(UUID target, BanEntry entry, boolean unbanned, String unbannedDate, String unbannedBy) {
        List<Map<String, Object>> history = banHistory.computeIfAbsent(target, k -> new ArrayList<>());
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("reason", entry.getReason());
        record.put("duration", entry.getDuration());
        record.put("moderator", entry.getModerator());
        record.put("date", PlayerDataManager.nowIsoFrom(entry.getDateEpochMillis()));
        if (!entry.isPermanent()) {
            record.put("expires", PlayerDataManager.nowIsoFrom(entry.getExpiresEpochMillis()));
        }
        record.put("unbanned", unbanned);
        history.add(record);
    }

    public String buildKickMessage(BanEntry entry) {
        String templateKey = entry.isPermanent() ? "ban.message" : "ban.temp-message";
        String template = plugin.getConfig().getString(templateKey, "&cYou are banned: {reason}");
        String playerName = Bukkit.getOfflinePlayer(entry.getUuid()).getName();
        String duration = entry.isPermanent() ? "Permanent" : entry.getDuration();
        String remaining = entry.isPermanent() ? "Permanent" : DurationUtil.format(entry.getRemainingMillis());

        String message = template
                .replace("{player}", playerName != null ? playerName : "Unknown")
                .replace("{duration}", duration)
                .replace("{remaining}", remaining)
                .replace("{reason}", entry.getReason() != null ? entry.getReason() : "No reason given")
                .replace("{moderator}", entry.getModerator() != null ? entry.getModerator() : "Console");

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public void checkExpiredBans() {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, BanEntry> entry : activeBans.entrySet()) {
            if (entry.getValue().isExpired()) {
                toRemove.add(entry.getKey());
            }
        }
        for (UUID uuid : toRemove) {
            unban(uuid, "AutoUnban");
            plugin.getLogger().info("Auto-unbanned expired ban for " + uuid);
        }
    }

    public boolean isBanned(UUID uuid) {
        BanEntry entry = activeBans.get(uuid);
        if (entry == null) return false;
        if (entry.isExpired()) {
            unban(uuid, "AutoUnban");
            return false;
        }
        return true;
    }

    public BanEntry getBan(UUID uuid) {
        return activeBans.get(uuid);
    }

    public Collection<BanEntry> getAllBans() {
        return activeBans.values();
    }

    public List<Map<String, Object>> getHistory(UUID uuid) {
        return banHistory.getOrDefault(uuid, Collections.emptyList());
    }

    /**
     * Called when a third-party plugin's ban is detected (e.g. via a matching event
     * from GriefPrevention, Anti-Spam, etc.) so it can be logged and announced consistently.
     */
    public void recordThirdPartyBan(UUID target, String reason, String sourcePlugin) {
        BanEntry entry = new BanEntry(target);
        entry.setReason(reason);
        entry.setDuration("permanent");
        entry.setPermanent(true);
        entry.setModerator(sourcePlugin);
        entry.setSource(sourcePlugin);
        entry.setDateEpochMillis(System.currentTimeMillis());
        entry.setExpiresEpochMillis(-1);

        activeBans.put(target, entry);
        recordHistory(target, entry, false, null, null);
        save();

        plugin.getDiscordManager().announceThirdPartyBan(target, sourcePlugin, reason);
    }
}
