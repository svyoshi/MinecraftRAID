package com.minecraftraid;

import com.minecraftraid.command.RaidCommand;
import com.minecraftraid.config.RaidConfig;
import com.minecraftraid.listener.AdminZoneTitleListener;
import com.minecraftraid.listener.ContainerAccessListener;
import com.minecraftraid.listener.ExplosionListener;
import com.minecraftraid.listener.ForeignClaimProtectionListener;
import com.minecraftraid.listener.PistonListener;
import com.minecraftraid.listener.RaidBlockEnvironmentListener;
import com.minecraftraid.listener.RaidBlockListener;
import com.minecraftraid.listener.ReinforcementListener;
import com.minecraftraid.listener.RepairListener;
import com.minecraftraid.listener.SafeZoneProtectionListener;
import com.minecraftraid.model.LandClaim;
import com.minecraftraid.model.RaidBlock;
import com.minecraftraid.persistence.RaidStatePersistence;
import com.minecraftraid.registry.ClaimRegistry;
import com.minecraftraid.reinforcement.ReinforcementManager;
import com.minecraftraid.registry.RaidBlockRegistry;
import com.minecraftraid.task.AutosaveTask;
import com.minecraftraid.task.ClaimBorderVisualTask;
import com.minecraftraid.task.RaidLookDurabilityTask;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class MinecraftRaidPlugin extends JavaPlugin implements Listener {

    private final RaidConfig raidConfig = new RaidConfig();
    private final RaidBlockRegistry blockRegistry = new RaidBlockRegistry();
    private final ClaimRegistry claimRegistry = new ClaimRegistry();
    private RaidStatePersistence persistence;
    private final AutosaveTask autosaveTask = new AutosaveTask();
    private final RaidLookDurabilityTask lookDurabilityTask = new RaidLookDurabilityTask();
    private ClaimBorderVisualTask claimBorderVisualTask;
    private ReinforcementManager reinforcementManager;

    @Override
    public void onEnable() {
        persistence = new RaidStatePersistence(this);
        raidConfig.reload(this);
        persistence.load(blockRegistry, claimRegistry);
        reinforcementManager = new ReinforcementManager(this, raidConfig, blockRegistry, claimRegistry);
        getServer().getPluginManager().registerEvents(
                new RaidBlockListener(this, raidConfig, blockRegistry), this);
        getServer().getPluginManager().registerEvents(
                new ForeignClaimProtectionListener(raidConfig, claimRegistry, blockRegistry), this);
        getServer().getPluginManager().registerEvents(new SafeZoneProtectionListener(claimRegistry), this);
        getServer().getPluginManager().registerEvents(new AdminZoneTitleListener(raidConfig, claimRegistry), this);
        getServer().getPluginManager().registerEvents(
                new ExplosionListener(raidConfig, blockRegistry, claimRegistry, getServer()), this);
        getServer().getPluginManager().registerEvents(new RaidBlockEnvironmentListener(blockRegistry), this);
        getServer().getPluginManager().registerEvents(new PistonListener(raidConfig, blockRegistry), this);
        getServer().getPluginManager().registerEvents(new RepairListener(raidConfig, blockRegistry), this);
        getServer().getPluginManager().registerEvents(new ReinforcementListener(raidConfig, reinforcementManager), this);
        getServer().getPluginManager().registerEvents(new ContainerAccessListener(claimRegistry, blockRegistry), this);
        getServer().getPluginManager().registerEvents(this, this);
        RaidCommand raidCommand = new RaidCommand(this, raidConfig, reinforcementManager);
        PluginCommand cmd = getCommand("raid");
        if (cmd != null) {
            cmd.setExecutor(raidCommand);
            cmd.setTabCompleter(raidCommand);
        } else {
            getLogger().severe("Command 'raid' missing from plugin.yml");
        }
        autosaveTask.start(this, blockRegistry, claimRegistry, persistence, raidConfig.saveIntervalTicks());
        lookDurabilityTask.start(this, raidConfig, blockRegistry);
        claimBorderVisualTask = new ClaimBorderVisualTask(this, raidConfig, claimRegistry);
        claimBorderVisualTask.start();
        getLogger().info("Minecraft Raid enabled. Raid blocks loaded: " + blockRegistry.size()
                + ", claims: " + claimRegistry.all().size());
    }

    @Override
    public void onDisable() {
        if (reinforcementManager != null) {
            reinforcementManager.shutdown();
        }
        if (claimBorderVisualTask != null) {
            claimBorderVisualTask.stop();
        }
        lookDurabilityTask.stop();
        autosaveTask.stop();
        List<RaidBlock> b = new ArrayList<>(blockRegistry.snapshot());
        List<LandClaim> c = new ArrayList<>(claimRegistry.all());
        persistence.save(b, c);
    }

    public void reloadRaidConfig() {
        raidConfig.reload(this);
        if (reinforcementManager != null) {
            reinforcementManager.onReload();
        }
        autosaveTask.stop();
        autosaveTask.start(this, blockRegistry, claimRegistry, persistence, raidConfig.saveIntervalTicks());
        lookDurabilityTask.stop();
        lookDurabilityTask.start(this, raidConfig, blockRegistry);
        if (claimBorderVisualTask != null) {
            claimBorderVisualTask.stop();
        }
        claimBorderVisualTask = new ClaimBorderVisualTask(this, raidConfig, claimRegistry);
        claimBorderVisualTask.start();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (reinforcementManager != null) {
            reinforcementManager.onPlayerQuit(event.getPlayer());
        }
        if (claimBorderVisualTask != null) {
            claimBorderVisualTask.handlePlayerQuit(event.getPlayer());
        }
        List<RaidBlock> b = new ArrayList<>(blockRegistry.snapshot());
        List<LandClaim> c = new ArrayList<>(claimRegistry.all());
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> persistence.save(b, c));
    }

    public RaidConfig getRaidConfig() {
        return raidConfig;
    }

    public RaidBlockRegistry getBlockRegistry() {
        return blockRegistry;
    }

    public ClaimRegistry getClaimRegistry() {
        return claimRegistry;
    }
}
