package com.minecraftraid.model;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record LandClaim(
        String id,
        UUID worldId,
        int centerBlockX,
        int centerBlockZ,
        int radiusBlocks,
        UUID ownerUuid,
        ClaimKind kind,
        Set<UUID> trustedUuids
) {
    public LandClaim {
        trustedUuids = Set.copyOf(trustedUuids == null ? Set.of() : trustedUuids);
    }

    public static final UUID ADMIN_SENTINEL_UUID = new UUID(0L, 0L);

    /** Standard player claim ({@link ClaimKind#PLAYER}); no trusted entries. */
    public LandClaim(
            String id,
            UUID worldId,
            int centerBlockX,
            int centerBlockZ,
            int radiusBlocks,
            UUID ownerUuid) {
        this(id, worldId, centerBlockX, centerBlockZ, radiusBlocks, ownerUuid, ClaimKind.PLAYER, Set.of());
    }

    /** Loaded claim with kind and no trusted list (trusted defaults empty). */
    public LandClaim(
            String id,
            UUID worldId,
            int centerBlockX,
            int centerBlockZ,
            int radiusBlocks,
            UUID ownerUuid,
            ClaimKind kind) {
        this(id, worldId, centerBlockX, centerBlockZ, radiusBlocks, ownerUuid, kind, Set.of());
    }

    /** Admin SAFE or WAR zone; sentinel owner UUID. */
    public static LandClaim adminZone(String id, UUID worldId, int cx, int cz, int radius, ClaimKind zoneKind) {
        if (zoneKind != ClaimKind.SAFE_ZONE && zoneKind != ClaimKind.WAR_ZONE) {
            throw new IllegalArgumentException("adminZone requires SAFE_ZONE or WAR_ZONE");
        }
        return new LandClaim(id, worldId, cx, cz, radius, ADMIN_SENTINEL_UUID, zoneKind, Set.of());
    }

    public static UUID adminSentinelUuid() {
        return ADMIN_SENTINEL_UUID;
    }

    public boolean isAdminZone() {
        return kind == ClaimKind.SAFE_ZONE || kind == ClaimKind.WAR_ZONE;
    }

    /** Owner or trusted on a PLAYER claim (admin zones ignore trusted). */
    public boolean isMember(UUID uuid) {
        if (!kind.isPlayerOwned()) {
            return ownerUuid.equals(uuid);
        }
        return ownerUuid.equals(uuid) || trustedUuids.contains(uuid);
    }

    public boolean isTrusted(UUID uuid) {
        return trustedUuids.contains(uuid);
    }

    public LandClaim withTrustAdded(UUID uuid) {
        if (!kind.isPlayerOwned() || ownerUuid.equals(uuid) || trustedUuids.contains(uuid)) {
            return this;
        }
        HashSet<UUID> n = new HashSet<>(trustedUuids);
        n.add(uuid);
        return new LandClaim(id, worldId, centerBlockX, centerBlockZ, radiusBlocks, ownerUuid, kind, n);
    }

    public LandClaim withTrustRemoved(UUID uuid) {
        if (!trustedUuids.contains(uuid)) {
            return this;
        }
        HashSet<UUID> n = new HashSet<>(trustedUuids);
        n.remove(uuid);
        return new LandClaim(id, worldId, centerBlockX, centerBlockZ, radiusBlocks, ownerUuid, kind, n);
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
