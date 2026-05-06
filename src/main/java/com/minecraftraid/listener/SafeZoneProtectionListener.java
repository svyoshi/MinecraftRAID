package com.minecraftraid.listener;

import com.minecraftraid.model.ClaimKind;
import com.minecraftraid.model.LandClaim;
import com.minecraftraid.registry.ClaimRegistry;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Safe zones: no damage taken by {@link Player}s inside a {@link ClaimKind#SAFE_ZONE}.
 * <p>Players with feet or eyes inside a safe zone cannot deal {@link EntityDamageByEntityEvent} damage to
 * {@link Player}s outside that zone. Uses {@link ClaimRegistry#safeZoneAt} and {@link DamageSource} so Paper
 * attribution matches melee and projectiles.
 */
public final class SafeZoneProtectionListener implements Listener {

    private final ClaimRegistry claims;

    public SafeZoneProtectionListener(ClaimRegistry claims) {
        this.claims = claims;
    }

    /**
     * Single {@link EventPriority#MONITOR} pass: cancel last so other plugins cannot undo the safe-zone rules.
     * {@code ignoreCancelled = false} so we still enforce when the event was already cancelled for other reasons.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onDamageMonitor(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        LandClaim victimSafe = claims.safeZoneAt(victim.getLocation());
        if (victimSafe != null) {
            event.setCancelled(true);
            return;
        }

        if (!(event instanceof EntityDamageByEntityEvent dbe)) {
            return;
        }
        Player attacker = resolvePlayerAttacker(dbe);
        if (attacker == null) {
            return;
        }
        LandClaim atkSafe = safeZoneCoveringPlayer(attacker);
        if (atkSafe == null) {
            return;
        }
        if (!atkSafe.contains(victim.getLocation())) {
            event.setCancelled(true);
        }
    }

    private LandClaim safeZoneCoveringPlayer(Player player) {
        LandClaim atFeet = claims.safeZoneAt(player.getLocation());
        if (atFeet != null) {
            return atFeet;
        }
        return claims.safeZoneAt(player.getEyeLocation());
    }

    private static Player resolvePlayerAttacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        DamageSource ds = event.getDamageSource();
        Entity cause = ds.getCausingEntity();
        if (cause instanceof Player p) {
            return p;
        }
        Entity direct = ds.getDirectEntity();
        if (direct instanceof Player p) {
            return p;
        }
        return null;
    }
}
