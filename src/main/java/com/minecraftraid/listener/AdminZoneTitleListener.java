package com.minecraftraid.listener;

import com.minecraftraid.config.RaidConfig;
import com.minecraftraid.model.ClaimKind;
import com.minecraftraid.model.LandClaim;
import com.minecraftraid.registry.ClaimRegistry;
import com.minecraftraid.util.Messages;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;

/**
 * Titles when crossing into or out of admin {@link ClaimKind#SAFE_ZONE} / {@link ClaimKind#WAR_ZONE} columns.
 * Player claims are ignored.
 */
public final class AdminZoneTitleListener implements Listener {

    private final RaidConfig config;
    private final ClaimRegistry claims;

    public AdminZoneTitleListener(RaidConfig config, ClaimRegistry claims) {
        this.config = config;
        this.claims = claims;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        handleXZChange(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        handleXZChange(event.getPlayer(), event.getFrom(), event.getTo());
    }

    private void handleXZChange(Player player, Location from, Location to) {
        if (!config.zoneTitlesEnabled()) {
            return;
        }
        if (from.getWorld() == null || to.getWorld() == null) {
            return;
        }
        if (from.getWorld().equals(to.getWorld())
                && from.getBlockX() == to.getBlockX()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        ClaimKind fromKind = adminKind(from);
        ClaimKind toKind = adminKind(to);
        if (Objects.equals(fromKind, toKind)) {
            return;
        }
        int fi = config.zoneTitleFadeInTicks();
        int st = config.zoneTitleStayTicks();
        int fo = config.zoneTitleFadeOutTicks();
        if (fromKind != null) {
            Messages.showTitleMini(
                    player,
                    config.zoneLeaveTitle(fromKind),
                    config.zoneLeaveSubtitle(fromKind),
                    fi, st, fo);
        }
        if (toKind != null) {
            Messages.showTitleMini(
                    player,
                    config.zoneEnterTitle(toKind),
                    config.zoneEnterSubtitle(toKind),
                    fi, st, fo);
        }
    }

    /** {@link ClaimKind#SAFE_ZONE} or {@link ClaimKind#WAR_ZONE} covering this column, or null. */
    private ClaimKind adminKind(Location loc) {
        LandClaim c = claims.anyClaimAt(loc);
        return (c != null && c.isAdminZone()) ? c.kind() : null;
    }
}
