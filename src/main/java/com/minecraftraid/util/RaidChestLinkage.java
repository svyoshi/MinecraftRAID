package com.minecraftraid.util;

import com.minecraftraid.model.RaidBlock;
import com.minecraftraid.registry.RaidBlockRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest.Type;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Merge double {@link Material#CHEST} / {@link Material#TRAPPED_CHEST}: one canonical {@link RaidBlock},
 * persisted once in SQLite with an alias coordinate for lookups.
 */
public final class RaidChestLinkage {

    private static final BlockFace[] CARDINAL_HORIZONTAL = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private RaidChestLinkage() {
    }

    public static boolean isRaidChest(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST;
    }

    /**
     * Deferred merge: inventory / block halves are usually not BOTH updated until after the placing tick.
     */
    public static void scheduleTryMerge(JavaPlugin plugin, RaidBlockRegistry registry, Block placed) {
        UUID worldId = placed.getWorld().getUID();
        int x = placed.getX();
        int y = placed.getY();
        int z = placed.getZ();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            World world = plugin.getServer().getWorld(worldId);
            if (world == null) {
                return;
            }
            tryMergeAt(registry, world.getBlockAt(x, y, z));
        });
    }

    /**
     * Attempt to unify two halves at this block coordinate and its double-chest partner (if any).
     *
     * @return {@code true} if a merge or re-alias was performed
     */
    public static boolean tryMergeAt(RaidBlockRegistry registry, Block placed) {
        if (!isRaidChest(placed.getType())) {
            return false;
        }

        Block partnerBlock = findPartnerChestHalfBlock(placed);
        if (partnerBlock != null) {
            RaidBlock ra = registry.get(placed.getWorld(), placed.getX(), placed.getY(), placed.getZ());
            RaidBlock rb = registry.get(partnerBlock.getWorld(), partnerBlock.getX(), partnerBlock.getY(),
                    partnerBlock.getZ());
            if (ra == null && rb == null) {
                return false;
            }
            applyMerge(registry, placed.getWorld(), cornerOf(placed), cornerOf(partnerBlock), placed.getType());
            return true;
        }

        // Fallback when block-data search fails but tile inventory is already double
        if (!(placed.getState() instanceof Chest chestState)) {
            return false;
        }
        Inventory inv = chestState.getInventory();
        if (!(inv instanceof DoubleChestInventory dc)) {
            return false;
        }
        Location left = chestBlockLocation(dc.getLeftSide());
        Location right = chestBlockLocation(dc.getRightSide());
        if (left == null || right == null || !Objects.equals(left.getWorld(), right.getWorld())) {
            return false;
        }
        applyMerge(registry, placed.getWorld(), left, right, placed.getType());
        return true;
    }

    public static void consolidateDoubleChestRaidBlocks(JavaPlugin plugin, RaidBlockRegistry registry) {
        boolean changed = true;
        int guard = 0;
        List<RaidBlock> batch = new ArrayList<>();
        while (changed && guard++ < 256) {
            changed = false;
            batch.clear();
            batch.addAll(registry.snapshot());
            for (RaidBlock rb : batch) {
                if (!isRaidChest(rb.material())) {
                    continue;
                }
                World w = plugin.getServer().getWorld(rb.worldId());
                if (w == null) {
                    continue;
                }
                Block b = w.getBlockAt(rb.x(), rb.y(), rb.z());
                if (!isRaidChest(b.getType())) {
                    continue;
                }
                RaidBlock rl = registry.get(w, b.getX(), b.getY(), b.getZ());
                if (rl == null) {
                    continue;
                }
                if (tryMergeAt(registry, b)) {
                    changed = true;
                    break;
                }
            }
        }
    }

    private static Location cornerOf(Block b) {
        return new Location(b.getWorld(), b.getX(), b.getY(), b.getZ());
    }

    /** Other half of a formed double chest, or {@code null} if SINGLE or mismatched neighbor. */
    private static Block findPartnerChestHalfBlock(Block block) {
        Material mat = block.getType();
        if (!isRaidChest(mat)) {
            return null;
        }
        BlockData bd = block.getBlockData();
        if (!(bd instanceof org.bukkit.block.data.type.Chest self)) {
            return null;
        }
        Type selfType = self.getType();
        if (selfType != Type.LEFT && selfType != Type.RIGHT) {
            return null;
        }
        org.bukkit.block.BlockFace facing = self.getFacing();

        for (BlockFace dir : CARDINAL_HORIZONTAL) {
            Block rel = block.getRelative(dir);
            if (rel.getType() != mat) {
                continue;
            }
            BlockData bd2 = rel.getBlockData();
            if (!(bd2 instanceof org.bukkit.block.data.type.Chest other)) {
                continue;
            }
            if (other.getFacing() != facing) {
                continue;
            }
            Type ot = other.getType();
            if ((selfType == Type.LEFT && ot == Type.RIGHT) || (selfType == Type.RIGHT && ot == Type.LEFT)) {
                return rel;
            }
        }
        return null;
    }

    private static void applyMerge(RaidBlockRegistry registry, World world,
            Location halfA, Location halfB, Material chestFallback) {
        UUID wid = world.getUID();
        int ax = halfA.getBlockX(), ay = halfA.getBlockY(), az = halfA.getBlockZ();
        int bx = halfB.getBlockX(), by = halfB.getBlockY(), bz = halfB.getBlockZ();
        RaidBlock ra = registry.get(world, ax, ay, az);
        RaidBlock rb = registry.get(world, bx, by, bz);

        Location canonCorner = canonicallyLowerCoords(halfA, halfB);
        int cx = canonCorner.getBlockX(), cy = canonCorner.getBlockY(), cz = canonCorner.getBlockZ();
        Location otherCorner = sameBlock(halfA, canonCorner) ? cornerAt(world, bx, by, bz)
                : cornerAt(world, ax, ay, az);

        Material mat = ra != null && isRaidChest(ra.material())
                ? ra.material()
                : (rb != null && isRaidChest(rb.material())
                ? rb.material()
                : chestFallback);

        RaidBlock merged;
        if (ra != null && rb != null) {
            if (!ra.ownerUuid().equals(rb.ownerUuid())) {
                return;
            }
            merged = combineHalfStats(ra, rb, cx, cy, cz, mat);
            registry.remove(wid, ax, ay, az);
            registry.remove(wid, bx, by, bz);
        } else if (ra != null) {
            merged = ra.withPosition(cx, cy, cz);
            registry.remove(wid, ax, ay, az);
        } else if (rb != null) {
            merged = rb.withPosition(cx, cy, cz);
            registry.remove(wid, bx, by, bz);
        } else {
            return;
        }

        registry.put(merged);
        if (!sameBlockXYZ(canonCorner, otherCorner)) {
            registry.putAlias(wid, otherCorner.getBlockX(), otherCorner.getBlockY(), otherCorner.getBlockZ(), merged);
        }
    }

    private static Location cornerAt(World w, int x, int y, int z) {
        return new Location(w, x, y, z);
    }

    private static RaidBlock combineHalfStats(RaidBlock a, RaidBlock b, int cx, int cy, int cz, Material mat) {
        UUID owner = a.ownerUuid();
        int tier = Math.max(a.reinforcementTier(), b.reinforcementTier());
        boolean breached = a.breached() || b.breached();
        int maxHp = a.maxHp() + b.maxHp();
        int pooled = Math.min(maxHp, Math.max(0, a.currentHp()) + Math.max(0, b.currentHp()));
        int currentHp = breached ? 0 : pooled;
        return new RaidBlock(a.worldId(), cx, cy, cz, owner, maxHp, currentHp, mat, breached, tier);
    }

    private static Location chestBlockLocation(Inventory side) {
        if (side == null) {
            return null;
        }
        InventoryHolder h = side.getHolder();
        if (h instanceof Chest chest) {
            Block block = chest.getBlock();
            return new Location(block.getWorld(), block.getX(), block.getY(), block.getZ());
        }
        return null;
    }

    private static Location canonicallyLowerCoords(Location left, Location right) {
        if (compareBlockCoords(left, right) <= 0) {
            return toImmutableCorner(left);
        }
        return toImmutableCorner(right);
    }

    private static Location toImmutableCorner(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private static int compareBlockCoords(Location a, Location b) {
        int c = Integer.compare(a.getBlockX(), b.getBlockX());
        if (c != 0) {
            return c;
        }
        c = Integer.compare(a.getBlockY(), b.getBlockY());
        if (c != 0) {
            return c;
        }
        return Integer.compare(a.getBlockZ(), b.getBlockZ());
    }

    private static boolean sameBlock(Location a, Location b) {
        return compareBlockCoords(a, b) == 0;
    }

    private static boolean sameBlockXYZ(Location a, Location b) {
        return compareBlockCoords(a, b) == 0;
    }
}
