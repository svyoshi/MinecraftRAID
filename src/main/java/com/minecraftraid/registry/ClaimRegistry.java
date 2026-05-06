package com.minecraftraid.registry;

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

    public List<LandClaim> ownedBy(UUID owner) {
        List<LandClaim> list = new ArrayList<>();
        for (LandClaim c : byId.values()) {
            if (c.ownerUuid().equals(owner)) {
                list.add(c);
            }
        }
        return list;
    }

    public LandClaim claimContaining(Player player, Location loc) {
        UUID w = loc.getWorld().getUID();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        for (LandClaim c : byId.values()) {
            if (c.ownerUuid().equals(player.getUniqueId()) && c.containsXZ(w, x, z)) {
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

    /** Any claim covering this block column (XZ), or null. */
    public LandClaim claimAt(Location loc) {
        return anyClaimAt(loc);
    }

    /** True if {@code loc} is inside a claim owned by someone other than {@code player}. */
    public boolean isForeignClaim(Player player, Location loc) {
        LandClaim c = anyClaimAt(loc);
        return c != null && !c.ownerUuid().equals(player.getUniqueId());
    }

    public boolean isOwnerLocation(Player player, Location loc) {
        LandClaim at = anyClaimAt(loc);
        return at != null && at.ownerUuid().equals(player.getUniqueId());
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
