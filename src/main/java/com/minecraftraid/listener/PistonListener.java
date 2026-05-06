package com.minecraftraid.listener;

import com.minecraftraid.config.RaidConfig;
import com.minecraftraid.registry.RaidBlockRegistry;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import java.util.List;

public final class PistonListener implements Listener {

    private final RaidConfig config;
    private final RaidBlockRegistry blocks;

    public PistonListener(RaidConfig config, RaidBlockRegistry blocks) {
        this.config = config;
        this.blocks = blocks;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onExtend(BlockPistonExtendEvent event) {
        if (!config.cancelPistonMove()) {
            return;
        }
        if (wouldMoveRaidBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onRetract(BlockPistonRetractEvent event) {
        if (!config.cancelPistonMove()) {
            return;
        }
        if (wouldMoveRaidBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private boolean wouldMoveRaidBlock(List<Block> moving) {
        for (Block b : moving) {
            if (blocks.get(b.getWorld(), b.getX(), b.getY(), b.getZ()) != null) {
                return true;
            }
        }
        return false;
    }
}
