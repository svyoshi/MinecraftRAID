package com.minecraftraid.model;

import org.bukkit.Location;

import java.util.UUID;

public record LandClaim(
        String id,
        UUID worldId,
        int centerBlockX,
        int centerBlockZ,
        int radiusBlocks,
        UUID ownerUuid,
        ClaimKind kind
) {
    public static final UUID ADMIN_SENTINEL_UUID = new UUID(0L, 0L);

    /** Standard player claim ({@link ClaimKind#PLAYER}). */
    public LandClaim(
            String id,
            UUID worldId,
            int centerBlockX,
            int centerBlockZ,
            int radiusBlocks,
            UUID ownerUuid) {
        this(id, worldId, centerBlockX, centerBlockZ, radiusBlocks, ownerUuid, ClaimKind.PLAYER);
    }

    /** Admin SAFE or WAR zone; sentinel owner UUID. */
    public static LandClaim adminZone(String id, UUID worldId, int cx, int cz, int radius, ClaimKind zoneKind) {
        if (zoneKind != ClaimKind.SAFE_ZONE && zoneKind != ClaimKind.WAR_ZONE) {
            throw new IllegalArgumentException("adminZone requires SAFE_ZONE or WAR_ZONE");
        }
        return new LandClaim(id, worldId, cx, cz, radius, ADMIN_SENTINEL_UUID, zoneKind);
    }

    public static UUID adminSentinelUuid() {
        return ADMIN_SENTINEL_UUID;
    }

    public boolean isAdminZone() {
        return kind == ClaimKind.SAFE_ZONE || kind == ClaimKind.WAR_ZONE;
    }

    public boolean containsXZ(UUID world, int blockX, int blockZ) {
        if (!worldId.equals(world)) {
            return false;
        }
        double dx = blockX - centerBlockX;
        double dz = blockZ - centerBlockZ;
        return dx * dx + dz * dz <= (double) radiusBlocks * radiusBlocks;
    }

    public boolean contains(Location loc) {
        return containsXZ(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockZ());
    }

    public boolean overlaps(LandClaim other) {
        if (!worldId.equals(other.worldId)) {
            return false;
        }
        double dx = centerBlockX - other.centerBlockX;
        double dz = centerBlockZ - other.centerBlockZ;
        double distSq = dx * dx + dz * dz;
        double sumR = (double) radiusBlocks + other.radiusBlocks;
        return distSq < sumR * sumR;
    }
}
