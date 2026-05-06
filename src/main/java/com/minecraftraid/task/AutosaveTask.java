package com.minecraftraid.task;

import com.minecraftraid.model.LandClaim;
import com.minecraftraid.model.RaidBlock;
import com.minecraftraid.persistence.RaidStatePersistence;
import com.minecraftraid.registry.ClaimRegistry;
import com.minecraftraid.registry.RaidBlockRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public final class AutosaveTask {

    private BukkitTask task;

    public void start(JavaPlugin plugin, RaidBlockRegistry blocks, ClaimRegistry claims, RaidStatePersistence persistence, long intervalTicks) {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<RaidBlock> b = new ArrayList<>(blocks.snapshot());
            List<LandClaim> c = new ArrayList<>(claims.all());
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> persistence.save(b, c));
        }, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
