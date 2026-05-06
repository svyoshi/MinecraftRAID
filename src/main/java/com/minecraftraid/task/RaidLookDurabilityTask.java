package com.minecraftraid.task;

import com.minecraftraid.config.RaidConfig;
import com.minecraftraid.model.RaidBlock;
import com.minecraftraid.registry.RaidBlockRegistry;
import com.minecraftraid.util.Messages;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public final class RaidLookDurabilityTask {

    private BukkitTask task;

    public void start(JavaPlugin plugin, RaidConfig config, RaidBlockRegistry blocks) {
        stop();
        long interval = Math.max(1L, config.lookDurabilityIntervalTicks());
        int maxBlocks = Math.max(1, (int) Math.ceil(config.lookDurabilityMaxDistance()));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                Block hit = player.getTargetBlockExact(maxBlocks, FluidCollisionMode.NEVER);
                if (hit == null) {
                    continue;
                }
                RaidBlock rb = blocks.get(hit.getWorld(), hit.getX(), hit.getY(), hit.getZ());
                if (rb == null) {
                    continue;
                }
                boolean show = rb.reinforcementTier() > 0 || rb.currentHp() < rb.maxHp();
                if (!show) {
                    continue;
                }
                Map<String, String> ph = new HashMap<>();
                ph.put("current", String.valueOf(rb.currentHp()));
                ph.put("max", String.valueOf(rb.maxHp()));
                ph.put("tier", tierRoman(rb.reinforcementTier()));
                Messages.actionBar(config, player, "look-durability", ph);
            }
        }, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private static String tierRoman(int reinforcementTier) {
        return switch (reinforcementTier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "\u2014";
        };
    }
}
