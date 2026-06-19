package com.minecraftraid.integration;

import com.minecraftraid.config.RaidConfig;
import com.minecraftraid.model.LandClaim;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Blocks player claims that overlap any non-global WorldGuard region.
 * All WorldGuard imports are isolated in this class for soft-dependency loading.
 */
public final class WorldGuardClaimRegionGuard implements ClaimRegionGuard {

    private static final String GLOBAL_REGION_ID = "__global__";
    private static final int PERIMETER_SAMPLES = 32;

    private final RaidConfig config;

    public WorldGuardClaimRegionGuard(RaidConfig config) {
        this.config = config;
    }

    @Override
    public boolean blocksClaim(LandClaim candidate, World world, int sampleY) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        int cx = candidate.centerBlockX();
        int cz = candidate.centerBlockZ();
        int r = candidate.radiusBlocks();
        int step = Math.max(1, config.worldGuardClaimCheckStepBlocks());

        if (hasNonGlobalRegion(query, world, cx, sampleY, cz)) {
            return true;
        }

        for (int i = 0; i < PERIMETER_SAMPLES; i++) {
            double angle = (2.0 * Math.PI * i) / PERIMETER_SAMPLES;
            int x = cx + (int) Math.round(r * Math.cos(angle));
            int z = cz + (int) Math.round(r * Math.sin(angle));
            if (hasNonGlobalRegion(query, world, x, sampleY, z)) {
                return true;
            }
        }

        int interiorStep = Math.max(step, Math.max(4, r / 16));
        int rSq = r * r;
        for (int x = cx - r; x <= cx + r; x += interiorStep) {
            for (int z = cz - r; z <= cz + r; z += interiorStep) {
                int dx = x - cx;
                int dz = z - cz;
                if (dx * dx + dz * dz > rSq) {
                    continue;
                }
                if (hasNonGlobalRegion(query, world, x, sampleY, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasNonGlobalRegion(RegionQuery query, World world, int x, int y, int z) {
        Location loc = new Location(world, x, y, z);
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        for (ProtectedRegion region : set) {
            if (!GLOBAL_REGION_ID.equals(region.getId())) {
                return true;
            }
        }
        return false;
    }
}
