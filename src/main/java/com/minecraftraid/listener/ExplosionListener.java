package com.minecraftraid.listener;

import com.minecraftraid.config.RaidConfig;
import com.minecraftraid.model.LandClaim;
import com.minecraftraid.model.RaidBlock;
import com.minecraftraid.registry.ClaimRegistry;
import com.minecraftraid.registry.DisconnectRaidGraceTracker;
import com.minecraftraid.registry.RaidBlockRegistry;
import com.minecraftraid.util.RaidMaterials;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class ExplosionListener implements Listener {

    private final RaidConfig config;
    private final RaidBlockRegistry blocks;
    private final ClaimRegistry claims;
    private final Server server;
    private final DisconnectRaidGraceTracker graceTracker;

    public ExplosionListener(
            RaidConfig config,
            RaidBlockRegistry blocks,
            ClaimRegistry claims,
            Server server,
            DisconnectRaidGraceTracker graceTracker) {
        this.config = config;
        this.blocks = blocks;
        this.claims = claims;
        this.server = server;
        this.graceTracker = graceTracker;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof Creeper) {
            Iterator<Block> it = event.blockList().iterator();
            while (it.hasNext()) {
                Block block = it.next();
                if (blocks.get(block.getWorld(), block.getX(), block.getY(), block.getZ()) != null) {
                    it.remove();
                    continue;
                }
                LandClaim c = claims.anyClaimAt(block.getLocation());
                if (c != null && c.isAdminZone()) {
                    it.remove();
                }
            }
            return;
        }
        handleDamage(event.blockList().iterator(), event.getYield());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleDamage(event.blockList().iterator(), 1f);
    }

    private void handleDamage(Iterator<Block> iterator, float yield) {
        Set<String> seenCanonRaid = new HashSet<>();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            LandClaim cz = claims.anyClaimAt(block.getLocation());
            if (cz != null && cz.isAdminZone()) {
                iterator.remove();
                continue;
            }
            RaidBlock rb = blocks.get(block.getWorld(), block.getX(), block.getY(), block.getZ());
            if (rb == null) {
                continue;
            }
            if (!seenCanonRaid.add(rb.positionKey())) {
                iterator.remove();
                continue;
            }
            if (!graceTracker.canOutsidersDamageOwner(rb.ownerUuid(), server, config)) {
                iterator.remove();
                continue;
            }
            double raw = config.explosionDamageScale() * Math.max(0.05f, yield);
            int dmg = Math.max(1, (int) Math.round(raw));
            int newHp = rb.currentHp() - dmg;
            if (newHp > 0) {
                blocks.replace(rb.withHp(newHp));
                iterator.remove();
            } else {
                if (RaidMaterials.isRaidContainer(rb.material())) {
                    blocks.replace(rb.withHp(0).withBreached(true));
                    iterator.remove();
                } else {
                    blocks.remove(block.getWorld(), block.getX(), block.getY(), block.getZ());
                }
            }
        }
    }
}
