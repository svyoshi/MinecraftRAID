package com.minecraftraid.model;

/** Stored on {@link LandClaim}; admin zones use {@link LandClaim#adminSentinelUuid()}. */
public enum ClaimKind {
    PLAYER,
    SAFE_ZONE,
    WAR_ZONE;

    public boolean isPlayerOwned() {
        return this == PLAYER;
    }
}
