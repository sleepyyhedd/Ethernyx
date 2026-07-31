package com.ethernyx.auth.models;

import java.util.UUID;

public class BanEntry {

    private final UUID uuid;
    private String reason;
    private String duration; // e.g. "7d" or "permanent"
    private String moderator;
    private UUID moderatorUuid;
    private long dateEpochMillis;
    private long expiresEpochMillis; // -1 if permanent
    private boolean permanent;
    private String source; // "EtherNyxAuth", "GriefPrevention", etc.

    public BanEntry(UUID uuid) {
        this.uuid = uuid;
        this.source = "EtherNyxAuth";
    }

    public boolean isExpired() {
        if (permanent) return false;
        if (expiresEpochMillis <= 0) return false;
        return System.currentTimeMillis() >= expiresEpochMillis;
    }

    public long getRemainingMillis() {
        if (permanent) return -1;
        return Math.max(0, expiresEpochMillis - System.currentTimeMillis());
    }

    public UUID getUuid() { return uuid; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getModerator() { return moderator; }
    public void setModerator(String moderator) { this.moderator = moderator; }

    public UUID getModeratorUuid() { return moderatorUuid; }
    public void setModeratorUuid(UUID moderatorUuid) { this.moderatorUuid = moderatorUuid; }

    public long getDateEpochMillis() { return dateEpochMillis; }
    public void setDateEpochMillis(long dateEpochMillis) { this.dateEpochMillis = dateEpochMillis; }

    public long getExpiresEpochMillis() { return expiresEpochMillis; }
    public void setExpiresEpochMillis(long expiresEpochMillis) { this.expiresEpochMillis = expiresEpochMillis; }

    public boolean isPermanent() { return permanent; }
    public void setPermanent(boolean permanent) { this.permanent = permanent; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
