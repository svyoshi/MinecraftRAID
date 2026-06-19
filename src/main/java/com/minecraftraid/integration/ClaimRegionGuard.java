package com.minecraftraid.integration;

import com.minecraftraid.model.LandClaim;
import org.bukkit.World;

/** Optional overlap checks before a land claim is created (e.g. WorldGuard regions). */
public interface ClaimRegionGuard {

    /**
     * @return true if the claim must be rejected due to an external protected region
     */
    boolean blocksClaim(LandClaim candidate, World world, int sampleY);
}
