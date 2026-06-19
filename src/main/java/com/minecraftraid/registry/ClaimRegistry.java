package com.minecraftraid.registry;

import com.minecraftraid.model.ClaimKind;
import com.minecraftraid.model.LandClaim;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimRegistry {

    private final Map<String, LandClaim> byId = new ConcurrentHashMap<>();

    public Collection<LandClaim> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /** Claims in the given world (for border visuals / queries). */
    public List<LandClaim> claimsInWorld(UUID worldId) {
        List<LandClaim> list = new ArrayList<>();
        for (LandClaim c : byId.values()) {
            if (c.worldId().equals(worldId)) {
                list.add(c);
            }
        }
        return list;
    }

    public void clear() {
        byId.clear();
    }

    public void add(LandClaim claim) {
        byId.put(claim.id(), claim);
    }

    public void replace(LandClaim updated) {
        byId.put(updated.id(), updated);
    }

    public LandClaim remove(String id) {
        return byId.remove(id);
    }

    public int countForOwner(UUID owner) {
        int n = 0;
        for (LandClaim c : byId.values()) {
            if (c.ownerUuid().equals(owner)) {
                n++;
            }
        }
        return n;
    }

    public boolean ownsAnyClaim(UUID owner) {
        return countForOwner(owner) > 0;
    }

    public List<LandClaim> ownedBy(UUID owner) {
        List<LandClaim> list = new ArrayList<>();
        for (LandClaim c : byId.values()) {
            if (c.ownerUuid().equals(owner)) {
                list.add(c);
            }
        }
        return list;
    }

    /**
     * A claim covering {@code loc} where this player counts as owner or trusted (PLAYER claims).
     */
    public LandClaim claimContaining(Player player, Location loc) {
        UUID w = loc.getWorld().getUID();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        for (LandClaim c : byId.values()) {
            if (!c.containsXZ(w, x, z)) {
                continue;
            }
            if (c.isMember(player.getUniqueId())) {
                return c;
            }
        }
        return null;
    }

    public LandClaim anyClaimAt(Location loc) {
        UUID w = loc.getWorld().getUID();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        for (LandClaim c : byId.values()) {
            if (c.containsXZ(w, x, z)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Any {@link ClaimKind#SAFE_ZONE} covering this column, or null. Unlike {@link #anyClaimAt}, this scans
     * specifically for safe zones so overlapping PLAYER (or other) claims do not hide the safe zone.
     */
    public LandClaim safeZoneAt(Location loc) {
        UUID w = loc.getWorld().getUID();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        for (LandClaim c : byId.values()) {
            if (c.kind() == ClaimKind.SAFE_ZONE && c.containsXZ(w, x, z)) {
                return c;
            }
        }
        return null;
    }

    /** Any claim covering this block column (XZ), or null. */
    public LandClaim claimAt(Location loc) {
        return anyClaimAt(loc);
    }

    /** PLAYER claim covering {@code loc} where {@code player} is owner or trusted. */
    public boolean isClaimMember(Player player, Location loc) {
        LandClaim c = anyClaimAt(loc);
        return c != null && c.isMember(player.getUniqueId());
    }

    /** True iff {@code loc} sits in a PLAYER claim owned by {@code player} (trusted does not qualify). */
    public boolean claimOwnedBy(Player player, Location loc) {
        LandClaim c = anyClaimAt(loc);
        return c != null && c.kind().isPlayerOwned() && c.ownerUuid().equals(player.getUniqueId());
    }

    /** @deprecated prefer {@link #isClaimMember} */
    @Deprecated
    public boolean isOwnerLocation(Player player, Location loc) {
        return isClaimMember(player, loc);
    }

    /** True if {@code loc} is inside a claim where {@code player} is not treated as owning/trusted member. */
    public boolean isForeignClaim(Player player, Location loc) {
        LandClaim c = anyClaimAt(loc);
        if (c == null) {
            return false;
        }
        return !c.isMember(player.getUniqueId());
    }

    /** Any circle overlap with another claim (different id), any kind/owner. Used for strict zoning. */
    public boolean overlapsAny(LandClaim candidate) {
        for (LandClaim existing : byId.values()) {
            if (existing.id().equals(candidate.id())) {
                continue;
            }
            if (existing.overlaps(candidate)) {
                return true;
            }
        }
        return false;
    }

    public boolean overlapsOthers(LandClaim candidate) {
        for (LandClaim existing : byId.values()) {
            if (existing.id().equals(candidate.id())) {
                continue;
            }
            if (!existing.overlaps(candidate)) {
                continue;
            }
            if (candidate.ownerUuid().equals(existing.ownerUuid())) {
                continue;
            }
            return true;
        }
        return false;
    }

    public boolean overlapsSameOwnerDisallowed(LandClaim candidate) {
        for (LandClaim existing : byId.values()) {
            if (existing.id().equals(candidate.id())) {
                continue;
            }
            if (!existing.overlaps(candidate)) {
                continue;
            }
            if (!candidate.ownerUuid().equals(existing.ownerUuid())) {
                continue;
            }
            return true;
        }
        return false;
    }
}
