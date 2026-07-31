package com.ethernyx.auth.managers;

import com.ethernyx.auth.EtherNyxAuth;
import com.ethernyx.auth.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Evaluates and grants achievements. Achievement requirements are checked
 * with simple hard-coded predicates (rather than a generic expression parser)
 * for reliability, but are keyed the same way as config.yml definitions so
 * names/descriptions/points can still be customized there.
 */
public class AchievementManager {

    public static final String FIRST_BLOOD = "first_blood";
    public static final String MONSTER_HUNTER = "monster_hunter";
    public static final String DIAMOND_MINER = "diamond_miner";
    public static final String NETHER_EXPLORER = "nether_explorer";
    public static final String THE_END = "the_end";
    public static final String WITHER_SLAYER = "wither_slayer";
    public static final String ENDER_DRAGON_SLAYER = "ender_dragon_slayer";
    public static final String MASTER_BUILDER = "master_builder";
    public static final String MILLIONAIRE = "millionaire";
    public static final String LEGENDARY = "legendary";

    private static final String[] ORDER = {
            FIRST_BLOOD, MONSTER_HUNTER, DIAMOND_MINER, NETHER_EXPLORER, THE_END,
            WITHER_SLAYER, ENDER_DRAGON_SLAYER, MASTER_BUILDER, MILLIONAIRE, LEGENDARY
    };

    private final EtherNyxAuth plugin;

    public AchievementManager(EtherNyxAuth plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("achievements.enabled", true);
    }

    public String getDisplayName(String key) {
        return ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("achievements.definitions." + key + ".name", key));
    }

    public String getDescription(String key) {
        return ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("achievements.definitions." + key + ".description", ""));
    }

    public int getPoints(String key) {
        return plugin.getConfig().getInt("achievements.definitions." + key + ".points", 0);
    }

    public String[] allKeys() {
        return ORDER;
    }

    /**
     * Re-evaluates all achievement conditions for a player and grants any newly earned ones.
     * Call after stat-affecting events (kills, mining, crafting, etc).
     */
    public void checkAll(Player player, PlayerData data) {
        if (!isEnabled()) return;

        grantIfEarned(player, data, FIRST_BLOOD, data.getKills() >= 1);
        grantIfEarned(player, data, MONSTER_HUNTER, data.getMobsKilled() >= 10);
        grantIfEarned(player, data, DIAMOND_MINER, data.getDiamondsMined() >= 1);
        grantIfEarned(player, data, NETHER_EXPLORER, data.hasEnteredNether());
        grantIfEarned(player, data, THE_END, data.hasEnteredEnd());
        grantIfEarned(player, data, WITHER_SLAYER, data.getWitherKills() >= 1);
        grantIfEarned(player, data, ENDER_DRAGON_SLAYER, data.getDragonKills() >= 1);
        grantIfEarned(player, data, MASTER_BUILDER, data.getBlocksPlaced() >= 10000);
        grantIfEarned(player, data, MILLIONAIRE, data.getItemsCollected() >= 1000000);
        grantIfEarned(player, data, LEGENDARY, player.getLevel() >= 50);
    }

    private void grantIfEarned(Player player, PlayerData data, String key, boolean earned) {
        if (!earned) return;
        if (data.getAchievements().contains(key)) return;

        data.getAchievements().add(key);

        String name = getDisplayName(key);
        player.sendMessage(plugin.prefix() + ChatColor.LIGHT_PURPLE + "Achievement Unlocked: " + name);

        boolean broadcast = plugin.getConfig().getBoolean("achievements.definitions." + key + ".broadcast", true);
        if (broadcast) {
            Bukkit.broadcastMessage(plugin.prefix() + ChatColor.LIGHT_PURPLE + player.getName()
                    + ChatColor.GRAY + " earned the achievement " + ChatColor.LIGHT_PURPLE + name);
        }
    }

    public Map<String, Boolean> getUpcoming(PlayerData data) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String key : ORDER) {
            result.put(key, data.getAchievements().contains(key));
        }
        return result;
    }
}
