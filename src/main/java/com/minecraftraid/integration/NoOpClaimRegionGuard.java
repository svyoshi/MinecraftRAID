package com.minecraftraid.integration;

import com.minecraftraid.model.LandClaim;
import org.bukkit.World;

public final class NoOpClaimRegionGuard implements ClaimRegionGuard {

    public static final NoOpClaimRegionGuard INSTANCE = new NoOpClaimRegionGuard();

    private NoOpClaimRegionGuard() {
    }

    @Override
    public boolean blocksClaim(LandClaim candidate, World world, int sampleY) {
        return false;
    }
}
