package com.minecraftraid.util;

import org.bukkit.Material;

/**
 * Block classification for raid mechanics. Ender chest is excluded (personal storage).
 */
public final class RaidMaterials {

    private RaidMaterials() {
    }

    public static boolean isRaidContainer(Material m) {
        if (!m.isBlock() || m.isAir()) {
            return false;
        }
        if (m == Material.ENDER_CHEST) {
            return false;
        }
        String n = m.name();
        if (n.endsWith("SHULKER_BOX")) {
            return true;
        }
        return switch (m) {
            case CHEST, TRAPPED_CHEST, BARREL, HOPPER, DISPENSER, DROPPER,
                 FURNACE, BLAST_FURNACE, SMOKER, BREWING_STAND, LECTERN,
                 SMITHING_TABLE, CARTOGRAPHY_TABLE, FLETCHING_TABLE, LOOM,
                 GRINDSTONE, STONECUTTER, DECORATED_POT, CRAFTER -> true;
            default -> false;
        };
    }
}
