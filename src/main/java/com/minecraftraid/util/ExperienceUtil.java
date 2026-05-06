package com.minecraftraid.util;

import org.bukkit.entity.Player;

/**
 * Total experience points aligned with vanilla ({@link Player#calculateTotalExperiencePoints()}).
 * Removal resets level/progress and re-applies the remainder via {@link Player#giveExp(int, boolean)} so the bar matches.
 */
public final class ExperienceUtil {

    private ExperienceUtil() {
    }

    public static int totalPoints(Player player) {
        return Math.max(0, player.calculateTotalExperiencePoints());
    }

    public static boolean tryRemove(Player player, int amount) {
        if (amount <= 0) {
            return true;
        }
        int t = totalPoints(player);
        if (t < amount) {
            return false;
        }
        int remainder = Math.max(0, t - amount);
        player.setLevel(0);
        player.setExp(0f);
        player.giveExp(remainder, false);
        return true;
    }
}
