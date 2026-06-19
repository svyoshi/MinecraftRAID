package com.minecraftraid.integration;

import com.minecraftraid.config.RaidConfig;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClaimRegionGuardFactory {

    private ClaimRegionGuardFactory() {
    }

    public static ClaimRegionGuard create(JavaPlugin plugin, RaidConfig config) {
        if (!config.worldGuardIntegrationEnabled()) {
            return NoOpClaimRegionGuard.INSTANCE;
        }
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return NoOpClaimRegionGuard.INSTANCE;
        }
        try {
            return new WorldGuardClaimRegionGuard(config);
        } catch (NoClassDefFoundError e) {
            plugin.getLogger().warning("WorldGuard is installed but the API is unavailable; claim overlap checks disabled.");
            return NoOpClaimRegionGuard.INSTANCE;
        }
    }
}
