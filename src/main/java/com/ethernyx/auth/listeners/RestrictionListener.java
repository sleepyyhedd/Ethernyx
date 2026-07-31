package com.ethernyx.auth.listeners;

import com.ethernyx.auth.EtherNyxAuth;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

import java.util.List;
import java.util.Locale;

/**
 * Enforces every restriction that must apply to a player who has not yet
 * logged in: no movement, no damage, no interaction, no chat/commands, etc.
 * All handlers bail out immediately (highest priority, ignoreCancelled=false)
 * for players who are already authenticated or bypassed.
 */
public class RestrictionListener implements Listener {

    private final EtherNyxAuth plugin;

    public RestrictionListener(EtherNyxAuth plugin) {
        this.plugin = plugin;
    }

    private boolean isRestricted(Player player) {
        if (player.hasPermission("ethernyx.auth.bypass")) return false;
        return !plugin.getAuthManager().isLoggedIn(player.getUniqueId());
    }

    // ---- Movement ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("security.movement.enabled", true)) return;
        Player player = event.getPlayer();
        if (!isRestricted(player)) return;

        // Only cancel actual positional movement, not just head rotation,
        // to avoid a jittery experience, but still block all translation.
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            if (plugin.getConfig().getBoolean("security.movement.teleport-back-on-move", true)) {
                event.setTo(event.getFrom());
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!isRestricted(player)) return;
        // Allow plugin-internal teleports (e.g. spawn placement on join) but block
        // player-initiated ones like ender pearls that might slip through.
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            event.setCancelled(true);
        }
    }

    // ---- Damage / health / food ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!plugin.getConfig().getBoolean("security.damage.prevent-all", true)) return;
        Player player = (Player) event.getEntity();
        if (!isRestricted(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!plugin.getConfig().getBoolean("security.health.freeze-food", true)) return;
        Player player = (Player) event.getEntity();
        if (!isRestricted(player)) return;
        event.setCancelled(true);
        player.setFoodLevel(plugin.getConfig().getInt("security.health.food-value", 20));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!plugin.getConfig().getBoolean("security.health.freeze-health", true)) return;
        Player player = (Player) event.getEntity();
        if (!isRestricted(player)) return;
        // We freeze health at max, so natural regen isn't needed anyway.
        event.setCancelled(true);
    }

    // ---- Blocks ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("security.interaction.prevent-block-break", true)) return;
        if (!isRestricted(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("security.interaction.prevent-block-place", true)) return;
        if (!isRestricted(event.getPlayer())) return;
        event.setCancelled(true);
    }

    // ---- Interaction ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("security.interaction.prevent-interact", true)) return;
        if (!isRestricted(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        if (!plugin.getConfig().getBoolean("security.interaction.prevent-item-pickup", true)) return;
        if (!isRestricted(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("security.interaction.prevent-item-drop", true)) return;
        if (!isRestricted(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!plugin.getConfig().getBoolean("security.interaction.prevent-inventory-open", true)) return;
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (!isRestricted(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!plugin.getConfig().getBoolean("security.interaction.prevent-inventory-click", true)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!isRestricted(player)) return;
        event.setCancelled(true);
    }

    // ---- Chat & commands ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("security.chat.prevent-chat", true)) return;
        Player player = event.getPlayer();
        if (!isRestricted(player)) return;
        event.setCancelled(true);
        player.sendMessage(plugin.prefix() + org.bukkit.ChatColor.RED + "You must log in before chatting.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("security.chat.prevent-command", true)) return;
        Player player = event.getPlayer();
        if (!isRestricted(player)) return;

        String commandLabel = event.getMessage().split(" ")[0].toLowerCase(Locale.ROOT);
        List<String> whitelist = plugin.getConfig().getStringList("security.chat.whitelist-commands");

        boolean allowed = false;
        for (String w : whitelist) {
            if (commandLabel.equalsIgnoreCase(w.trim())) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            event.setCancelled(true);
            player.sendMessage(plugin.prefix() + org.bukkit.ChatColor.RED
                    + "You must log in before using commands.");
        }
    }
}
