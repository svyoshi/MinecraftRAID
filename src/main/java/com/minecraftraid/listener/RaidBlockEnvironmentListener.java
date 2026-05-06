package com.minecraftraid.listener;

import com.minecraftraid.registry.RaidBlockRegistry;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

public final class RaidBlockEnvironmentListener implements Listener {

    private final RaidBlockRegistry blocks;

    public RaidBlockEnvironmentListener(RaidBlockRegistry blocks) {
        this.blocks = blocks;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBurn(BlockBurnEvent event) {
        if (isRaidBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSpread(BlockSpreadEvent event) {
        if (isRaidBlock(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        Block source = event.getSource();
        if (source != null && isRaidBlock(source)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onIgnite(BlockIgniteEvent event) {
        if (isRaidBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEndermanChange(EntityChangeBlockEvent event) {
        Block b = event.getBlock();
        if (b != null && isRaidBlock(b)) {
            event.setCancelled(true);
        }
    }

    private boolean isRaidBlock(Block block) {
        return blocks.get(block.getWorld(), block.getX(), block.getY(), block.getZ()) != null;
    }
}
