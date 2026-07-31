package com.ethernyx.auth.managers;

import com.ethernyx.auth.EtherNyxAuth;
import com.ethernyx.auth.models.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class BookManager {

    private final EtherNyxAuth plugin;

    public BookManager(EtherNyxAuth plugin) {
        this.plugin = plugin;
    }

    /**
     * Builds a written book item containing the target's full player info.
     */
    public ItemStack buildInfoBook(OfflinePlayer target, PlayerData data) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("book.title", "&5☾ {player} &5☾")
                        .replace("{player}", target.getName() != null ? target.getName() : "Unknown"));
        String author = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("book.author", "&7EtherNyx Archive"));

        meta.setTitle(title.length() > 32 ? title.substring(0, 32) : title);
        meta.setAuthor(author);

        meta.addPage(page1Identity(target, data));
        meta.addPage(page2Statistics(data));
        meta.addPage(page3Achievements(data));
        meta.addPage(page4History(data));
        meta.addPage(page5Location(target));

        book.setItemMeta(meta);
        return book;
    }

    private String page1Identity(OfflinePlayer target, PlayerData data) {
        StringBuilder sb = new StringBuilder();
        sb.append(c("&5☾ IDENTITY & STATUS ☾\n"));
        sb.append(c("&8━━━━━━━━━━━━━━━━━\n"));
        sb.append(c("&fName: &7")).append(target.getName()).append("\n");
        sb.append(c("&fUUID:\n&7")).append(shortUuid(target.getUniqueId().toString())).append("\n");
        sb.append(c("&fIP: &7")).append(nullSafe(data.getIp())).append("\n");
        sb.append(c("&fStatus: ")).append(target.isOnline() ? c("&aOnline") : c("&cOffline")).append("\n");
        sb.append(c("&fRegistered: &7")).append(nullSafe(data.getRegisteredDate())).append("\n");
        sb.append(c("&fLast Login: &7")).append(nullSafe(data.getLastLogin())).append("\n");
        sb.append(c("&fPlaytime: &7")).append(formatPlaytime(data.getPlaytimeSeconds())).append("\n");
        sb.append(c("&8━━━━━━━━━━━━━━━━━\n"));
        sb.append(c("&7&o\"I am bound to this realm.\""));
        return sb.toString();
    }

    private String page2Statistics(PlayerData data) {
        StringBuilder sb = new StringBuilder();
        sb.append(c("&5☾ STATISTICS ☾\n"));
        sb.append(c("&8━━━━━━━━━━━━━━━━━\n"));
        sb.append(c("&fKills: &7")).append(data.getKills()).append("\n");
        sb.append(c("&fDeaths: &7")).append(data.getDeaths()).append("\n");
        sb.append(c("&fKD Ratio: &7")).append(data.getKdRatio()).append("\n");
        sb.append(c("&fMobs Killed: &7")).append(data.getMobsKilled()).append("\n");
        sb.append(c("&fBlocks Mined: &7")).append(data.getBlocksMined()).append("\n");
        sb.append(c("&fBlocks Placed: &7")).append(data.getBlocksPlaced()).append("\n");
        sb.append(c("&fItems Crafted: &7")).append(data.getItemsCrafted()).append("\n");
        sb.append(c("&fDistance Walked: &7")).append(String.format(Locale.US, "%.1fm", data.getDistanceWalked()));
        return sb.toString();
    }

    private String page3Achievements(PlayerData data) {
        StringBuilder sb = new StringBuilder();
        sb.append(c("&5☾ ACHIEVEMENTS ☾\n"));
        sb.append(c("&8━━━━━━━━━━━━━━━━━\n"));
        sb.append(c("&fTotal: &7")).append(data.getAchievements().size())
                .append("/").append(plugin.getAchievementManager().allKeys().length).append("\n");
        sb.append(c("&8━━━━━━━━━━━━━━━━━\n"));

        for (String key : plugin.getAchievementManager().allKeys()) {
            boolean earned = data.getAchievements().contains(key);
            String name = plugin.getAchievementManager().getDisplayName(key);
            sb.append(earned ? c("&a✔ ") : c("&8✘ ")).append(name).append("\n");
        }
        return sb.toString();
    }

    private String page4History(PlayerData data) {
        StringBuilder sb = new StringBuilder();
        sb.append(c("&5☾ HISTORY ☾\n"));
        sb.append(c("&8━━━━━━━━━━━━━━━━━\n"));
        sb.append(c("&cRecent Deaths:\n"));
        appendList(sb, data.getDeathHistory(), 4);
        sb.append(c("\n&aRecent Kills:\n"));
        appendList(sb, data.getKillHistory(), 4);
        return sb.toString();
    }

    private String page5Location(OfflinePlayer target) {
        StringBuilder sb = new StringBuilder();
        sb.append(c("&5☾ LOCATION ☾\n"));
        sb.append(c("&8━━━━━━━━━━━━━━━━━\n"));

        if (target.isOnline() && target.getPlayer() != null) {
            Player p = target.getPlayer();
            sb.append(c("&fWorld: &7")).append(p.getWorld().getName()).append("\n");
            sb.append(c("&fX: &7")).append((int) p.getLocation().getX()).append("\n");
            sb.append(c("&fY: &7")).append((int) p.getLocation().getY()).append("\n");
            sb.append(c("&fZ: &7")).append((int) p.getLocation().getZ()).append("\n");
            sb.append(c("&fGamemode: &7")).append(p.getGameMode().name()).append("\n");
            sb.append(c("&fHealth: &7")).append(String.format(Locale.US, "%.1f", p.getHealth())).append("\n");
            sb.append(c("&fHunger: &7")).append(p.getFoodLevel()).append("\n");
            sb.append(c("&fLevel: &7")).append(p.getLevel());
        } else {
            sb.append(c("&7Player is currently offline."));
        }
        return sb.toString();
    }

    private void appendList(StringBuilder sb, List<String> entries, int max) {
        if (entries.isEmpty()) {
            sb.append(c("&8None recorded.\n"));
            return;
        }
        int count = Math.min(max, entries.size());
        for (int i = 0; i < count; i++) {
            sb.append(c("&7- ")).append(entries.get(i)).append("\n");
        }
    }

    private String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String nullSafe(String s) {
        return s != null ? s : "N/A";
    }

    private String shortUuid(String uuid) {
        return uuid;
    }

    private String formatPlaytime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }
}
