package com.minecraftraid.listener;

import com.minecraftraid.model.LandClaim;
import com.minecraftraid.model.RaidBlock;
import com.minecraftraid.registry.ClaimRegistry;
import com.minecraftraid.registry.RaidBlockRegistry;
import com.minecraftraid.util.RaidMaterials;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Blocks opening raid storage containers for non-claim-owners until breached (HP depleted).
 */
public final class ContainerAccessListener implements Listener {

    private final ClaimRegistry claims;
    private final RaidBlockRegistry blocks;

    public ContainerAccessListener(ClaimRegistry claims, RaidBlockRegistry blocks) {
        this.claims = claims;
        this.blocks = blocks;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        RaidBlock rb = blocks.get(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (rb == null || !RaidMaterials.isRaidContainer(rb.material())) {
            return;
        }
        if (rb.breached() || rb.currentHp() <= 0) {
            return;
        }
        Player player = event.getPlayer();
        if (canAccessContainer(player, block, rb)) {
            return;
        }
        event.setCancelled(true);
    }

    private boolean canAccessContainer(Player player, Block block, RaidBlock rb) {
        if (player.getUniqueId().equals(rb.ownerUuid())) {
            return true;
        }
        LandClaim claim = claims.claimAt(block.getLocation());
        UUID claimOwner = claim != null ? claim.ownerUuid() : rb.ownerUuid();
        return player.getUniqueId().equals(claimOwner);
    }
}
