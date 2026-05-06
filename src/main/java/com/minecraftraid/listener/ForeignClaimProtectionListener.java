package com.minecraftraid.listener;

import com.minecraftraid.config.RaidConfig;
import com.minecraftraid.model.ClaimKind;
import com.minecraftraid.model.LandClaim;
import com.minecraftraid.registry.ClaimRegistry;
import com.minecraftraid.registry.RaidBlockRegistry;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

/**
 * Outsiders cannot modify blocks or harm/interact with non-player entities inside another player's claim.
 * {@link Player} victims inside PLAYER circle claims remain damageable (PvP/raiding); SAFE/WAR zones use other rules.
 * Runs at {@link EventPriority#LOW} so {@link RaidBlockListener} at {@code HIGH} still handles raid HP first.
 */
public final class ForeignClaimProtectionListener implements Listener {

    private final RaidConfig config;
    private final ClaimRegistry claims;
    private final RaidBlockRegistry blocks;

    public ForeignClaimProtectionListener(RaidConfig config, ClaimRegistry claims, RaidBlockRegistry blocks) {
        this.config = config;
        this.claims = claims;
        this.blocks = blocks;
    }

    /** Admin SAFE/WAR: no edits without bypass. Player claims: foreign rule unchanged. */
    private boolean deniesClaimEdit(Player player, Location loc) {
        LandClaim c = claims.anyClaimAt(loc);
        if (c == null) {
            return false;
        }
        if (c.isAdminZone()) {
            return !player.hasPermission("minecraftraid.admin.zones");
        }
        return claims.isForeignClaim(player, loc);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlace(BlockPlaceEvent event) {
        if (!config.protectClaimBlocks()) {
            return;
        }
        Player player = event.getPlayer();
        if (deniesClaimEdit(player, event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        if (!config.protectClaimBlocks()) {
            return;
        }
        Player player = event.getPlayer();
        for (BlockState state : event.getReplacedBlockStates()) {
            if (deniesClaimEdit(player, state.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent event) {
        if (!config.protectClaimBlocks()) {
            return;
        }
        Block block = event.getBlock();
        if (blocks.get(block.getWorld(), block.getX(), block.getY(), block.getZ()) != null) {
            return;
        }
        Player player = event.getPlayer();
        if (deniesClaimEdit(player, block.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!config.protectClaimBlocks()) {
            return;
        }
        Player player = event.getPlayer();
        Location place = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
        if (deniesClaimEdit(player, place)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (!config.protectClaimEntities() && !config.protectClaimBlocks()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (deniesClaimEdit(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        LandClaim vc = claims.anyClaimAt(victim.getLocation());
        if (vc != null && vc.kind() == ClaimKind.WAR_ZONE) {
            return;
        }
        /* PLAYER circles: blocks/interaction stay protected against outsiders; entity damage stays on for Players
         * so non-members may still raid PvP the owner/trusted inside the claim. Animals & passives remain protected below. */
        if (vc != null && vc.kind() == ClaimKind.PLAYER && victim instanceof Player) {
            return;
        }
        if (!config.protectClaimEntities()) {
            return;
        }
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (claims.isForeignClaim(attacker, victim.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!config.protectClaimEntities()) {
            return;
        }
        Player player = event.getPlayer();
        if (deniesClaimEdit(player, event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!config.protectClaimEntities()) {
            return;
        }
        Player player = event.getPlayer();
        if (deniesClaimEdit(player, event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!config.protectClaimEntities()) {
            return;
        }
        Entity remover = event.getRemover();
        Player player = remover instanceof Player p ? p : null;
        if (player == null) {
            return;
        }
        if (deniesClaimEdit(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!config.protectClaimEntities()) {
            return;
        }
        Entity attacker = event.getAttacker();
        Player player = attacker != null ? resolvePlayerDamager(attacker) : null;
        if (player == null) {
            return;
        }
        if (deniesClaimEdit(player, event.getVehicle().getLocation())) {
            event.setCancelled(true);
        }
    }

    private static Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }
}
