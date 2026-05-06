package com.minecraftraid.model;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.UUID;

/** A placed block with raid HP. When {@code breached} is true on a container, outsiders may open it. */
public record RaidBlock(
        UUID worldId,
        int x,
        int y,
        int z,
        UUID ownerUuid,
        int maxHp,
        int currentHp,
        Material material,
        boolean breached,
        int reinforcementTier
) {
    public static final int MAX_REINFORCEMENT_TIER = 3;

    /** Place new raid block at tier 0. */
    public RaidBlock(UUID worldId, int x, int y, int z, UUID ownerUuid, int maxHp, int currentHp, Material material) {
        this(worldId, x, y, z, ownerUuid, maxHp, currentHp, material, false, 0);
    }

    public String positionKey() {
        return worldId + "|" + x + "|" + y + "|" + z;
    }

    public static String positionKey(UUID worldId, int x, int y, int z) {
        return worldId + "|" + x + "|" + y + "|" + z;
    }

    public boolean sameBlock(World world, int bx, int by, int bz) {
        return world.getUID().equals(worldId) && x == bx && y == by && z == bz;
    }

    public RaidBlock withHp(int newHp) {
        int clamped = Math.max(0, Math.min(maxHp, newHp));
        return new RaidBlock(worldId, x, y, z, ownerUuid, maxHp, clamped, material, breached, reinforcementTier);
    }

    public RaidBlock withBreached(boolean value) {
        return new RaidBlock(worldId, x, y, z, ownerUuid, maxHp, currentHp, material, value, reinforcementTier);
    }

    /** One tier up; adds {@code hpPerTier} to max and current HP (current capped). No-op if breached or max tier. */
    public RaidBlock withAppliedReinforcement(int hpPerTier) {
        if (breached || reinforcementTier >= MAX_REINFORCEMENT_TIER || hpPerTier <= 0) {
            return this;
        }
        int nt = reinforcementTier + 1;
        int nMax = maxHp + hpPerTier;
        int nCur = Math.min(currentHp + hpPerTier, nMax);
        return new RaidBlock(worldId, x, y, z, ownerUuid, nMax, nCur, material, breached, nt);
    }
}
