package com.minecraftraid.listener;

import com.minecraftraid.model.ClaimKind;
import com.minecraftraid.model.LandClaim;
import com.minecraftraid.registry.ClaimRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * No damage (including environment) inside {@link ClaimKind#SAFE_ZONE} claims.
 * Players with {@code minecraftraid.admin.zone.bypass} still take damage there.
 */
public final class SafeZoneProtectionListener implements Listener {

    private final ClaimRegistry claims;

    public SafeZoneProtectionListener(ClaimRegistry claims) {
        this.claims = claims;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        LandClaim c = claims.anyClaimAt(event.getEntity().getLocation());
        if (c == null || c.kind() != ClaimKind.SAFE_ZONE) {
            return;
        }
        if (event.getEntity() instanceof Player p && p.hasPermission("minecraftraid.admin.zone.bypass")) {
            return;
        }
        event.setCancelled(true);
    }
}
