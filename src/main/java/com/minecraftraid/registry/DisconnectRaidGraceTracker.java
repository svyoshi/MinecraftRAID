package com.minecraftraid.registry;

import com.minecraftraid.config.RaidConfig;
import org.bukkit.Server;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks post-disconnect grace windows so claim owners cannot instantly shield raid blocks by logging out.
 */
public final class DisconnectRaidGraceTracker {

    private final ConcurrentHashMap<UUID, Long> graceUntilMillis = new ConcurrentHashMap<>();

    public void onOwnerDisconnect(UUID ownerUuid, int graceMinutes) {
        if (graceMinutes <= 0) {
            graceUntilMillis.remove(ownerUuid);
            return;
        }
        long deadline = System.currentTimeMillis() + graceMinutes * 60_000L;
        graceUntilMillis.put(ownerUuid, deadline);
    }

    public boolean canOutsidersDamageOwner(UUID ownerUuid, Server server, RaidConfig config) {
        if (!config.requireOwnerOnlineForRaidDamage()) {
            return true;
        }
        if (server.getPlayer(ownerUuid) != null) {
            return true;
        }
        int graceMinutes = config.disconnectRaidGraceMinutes();
        if (graceMinutes <= 0) {
            return false;
        }
        Long deadline = graceUntilMillis.get(ownerUuid);
        if (deadline == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now >= deadline) {
            graceUntilMillis.remove(ownerUuid, deadline);
            return false;
        }
        return true;
    }
}
