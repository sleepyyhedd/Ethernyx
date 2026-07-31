package com.ethernyx.auth.listeners;

import com.ethernyx.auth.EtherNyxAuth;
import com.ethernyx.auth.managers.PlayerDataManager;
import com.ethernyx.auth.models.PlayerData;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

public class StatsListener implements Listener {

    private final EtherNyxAuth plugin;

    public StatsListener(EtherNyxAuth plugin) {
        this.plugin = plugin;
    }

    private boolean shouldTrack(Player player) {
        return plugin.getAuthManager().isLoggedIn(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!shouldTrack(player)) return;

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        data.incrementBlocksMined();

        Material type = event.getBlock().getType();
        if (type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE) {
            data.incrementDiamondsMined();
        }

        plugin.getAchievementManager().checkAll(player, data);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!shouldTrack(player)) return;

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        data.incrementBlocksPlaced();

        plugin.getAchievementManager().checkAll(player, data);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!shouldTrack(player)) return;

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        int amount = event.getRecipe().getResult().getAmount();
        data.incrementItemsCrafted(amount);

        plugin.getAchievementManager().checkAll(player, data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPickup(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        if (!shouldTrack(player)) return;

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        data.addItemsCollected(event.getItem().getItemStack().getAmount());

        plugin.getAchievementManager().checkAll(player, data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!shouldTrack(player)) return;
        if (event.getFrom().getWorld() != event.getTo().getWorld()) return;

        double distance = event.getFrom().distance(event.getTo());
        if (distance > 0 && distance < 10) { // sanity bound against teleport spikes
            PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
            data.addDistanceWalked(distance);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!shouldTrack(player)) return;

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        World.Environment env = player.getWorld().getEnvironment();
        if (env == World.Environment.NETHER) {
            data.setEnteredNether(true);
        } else if (env == World.Environment.THE_END) {
            data.setEnteredEnd(true);
        }

        plugin.getAchievementManager().checkAll(player, data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (!shouldTrack(killer)) return;

        // Player-vs-player kills are handled in onPlayerDeath to avoid double counting.
        if (event.getEntity() instanceof Player) return;

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(killer.getUniqueId());
        data.incrementMobsKilled();

        EntityType type = event.getEntityType();
        if (type == EntityType.WITHER) {
            data.incrementWitherKills();
        } else if (type == EntityType.ENDER_DRAGON) {
            data.incrementDragonKills();
        }

        plugin.getAchievementManager().checkAll(killer, data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        String cause = event.getDeathMessage() != null ? event.getDeathMessage() : "Unknown cause";
        String timestamp = PlayerDataManager.nowIso();

        if (shouldTrack(victim)) {
            PlayerData victimData = plugin.getPlayerDataManager().getOrCreate(victim.getUniqueId());
            victimData.incrementDeaths();
            victimData.addDeath(timestamp + ": " + cause);
        }

        Player killer = victim.getKiller();
        if (killer != null && shouldTrack(killer)) {
            PlayerData killerData = plugin.getPlayerDataManager().getOrCreate(killer.getUniqueId());
            killerData.incrementKills();
            killerData.addKill(timestamp + ": Killed " + victim.getName());
            plugin.getAchievementManager().checkAll(killer, killerData);
        }
    }
}
