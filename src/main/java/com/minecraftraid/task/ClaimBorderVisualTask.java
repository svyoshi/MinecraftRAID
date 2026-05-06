package com.minecraftraid.task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.minecraftraid.config.RaidConfig;
import com.minecraftraid.model.LandClaim;
import com.minecraftraid.registry.ClaimRegistry;

/**
 * Client-side claim ring: {@link Player#sendBlockChange(Location, BlockData)} and
 * {@link Player#spawnParticle} only — no {@code setType} on the server.
 * Fakes accumulate across ticks; only keys that leave the desired set (view/band) are reverted.
 */
public final class ClaimBorderVisualTask {

    private enum BorderStyle {
        FOREIGN(1),
        OWN(2),
        WAR(3),
        SAFE(4);

        final int rank;

        BorderStyle(int rank) {
            this.rank = rank;
        }

        static BorderStyle merge(BorderStyle a, BorderStyle b) {
            return a.rank >= b.rank ? a : b;
        }
    }

    private static BorderStyle styleFor(Player player, LandClaim claim) {
        return switch (claim.kind()) {
            case SAFE_ZONE -> BorderStyle.SAFE;
            case WAR_ZONE -> BorderStyle.WAR;
            case PLAYER ->
                    claim.ownerUuid().equals(player.getUniqueId()) ? BorderStyle.OWN : BorderStyle.FOREIGN;
        };
    }

    private static final BlockData LIME_WOOL = Material.LIME_WOOL.createBlockData();
    private static final BlockData RED_WOOL = Material.RED_WOOL.createBlockData();
    private static final BlockData ORANGE_WOOL = Material.ORANGE_WOOL.createBlockData();
    private static final BlockData BLACK_WOOL = Material.BLACK_WOOL.createBlockData();

    private final JavaPlugin plugin;
    private final RaidConfig config;
    private final ClaimRegistry claims;

    private BukkitTask task;
    /** Respawns dust every tick so the client-visible trail persists (dust is single-frame). */
    private BukkitTask dustTask;
    /** Per-player active client fakes keyed by {@code x + ":" + z} in the player's world. */
    private final Map<UUID, ConcurrentHashMap<String, Location>> activeFakes = new ConcurrentHashMap<>();
    /** Last wool/particle category sent for each fake key. */
    private final Map<UUID, ConcurrentHashMap<String, BorderStyle>> activeStyles = new ConcurrentHashMap<>();
    /** Next index into angle-ordered keys when capping new sends per tick (rotates around the ring). */
    private final Map<UUID, Integer> ringCursor = new ConcurrentHashMap<>();
    /** Last world the ring cursor was advanced in; reset cursor only on dimension/world change. */
    private final Map<UUID, UUID> ringWorld = new ConcurrentHashMap<>();
    /** Rotates which ring samples receive dust when over per-tick dust cap. */
    private final Map<UUID, Integer> dustRingCursor = new ConcurrentHashMap<>();

    public ClaimBorderVisualTask(JavaPlugin plugin, RaidConfig config, ClaimRegistry claims) {
        this.plugin = plugin;
        this.config = config;
        this.claims = claims;
    }

    public void start() {
        stop();
        if (!config.borderVisualEnabled()) {
            return;
        }
        long interval = Math.max(1L, config.borderVisualIntervalTicks());
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
        dustTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::dustTick, 1L, 1L);
    }

    public void stop() {
        if (dustTask != null) {
            dustTask.cancel();
            dustTask = null;
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            revertAll(p);
        }
        activeFakes.clear();
        activeStyles.clear();
        ringCursor.clear();
        ringWorld.clear();
        dustRingCursor.clear();
    }

    /** Revert client blocks before disconnect (e.g. PlayerQuitEvent). */
    public void handlePlayerQuit(Player player) {
        revertAll(player);
        UUID id = player.getUniqueId();
        ringCursor.remove(id);
        ringWorld.remove(id);
        dustRingCursor.remove(id);
    }

    private void tick() {
        if (!config.borderVisualEnabled()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            DesiredRing ring = computeDesiredRing(player);
            pruneActiveFakes(player, ring.merged());
            emitRingBatch(player, ring);
        }
    }

    /** Re-spawn dust along active fakes still in the desired ring (dust does not persist client-side). */
    private void dustTick() {
        if (!config.borderVisualEnabled()) {
            return;
        }
        int cap = Math.max(64, config.borderMaxUpdatesPerTick() * 8);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            ConcurrentHashMap<String, Location> fakes = activeFakes.get(id);
            if (fakes == null || fakes.isEmpty()) {
                continue;
            }
            DesiredRing ring = computeDesiredRing(player);
            Map<String, BorderStyle> merged = ring.merged();
            Map<String, Integer> keyToIndex = ring.keyToIndex();
            List<String> keys = ring.keys();
            int n = keys.size();
            if (n == 0) {
                continue;
            }
            World world = player.getWorld();
            var rng = ThreadLocalRandom.current();
            int start = Math.floorMod(dustRingCursor.getOrDefault(id, 0), n);
            int spawned = 0;
            int step = 0;
            for (; step < n && spawned < cap; step++) {
                String key = keys.get(Math.floorMod(start + step, n));
                if (!fakes.containsKey(key) || !merged.containsKey(key)) {
                    continue;
                }
                BorderStyle borderStyle = merged.get(key);
                if (borderStyle == null) {
                    continue;
                }
                int colon = key.indexOf(':');
                int x = Integer.parseInt(key.substring(0, colon));
                int z = Integer.parseInt(key.substring(colon + 1));
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                int y = world.getHighestBlockYAt(x, z);
                Location blockLoc = new Location(world, x, y, z);
                spawnBorderDust(player, blockLoc, borderStyle, keyToIndex.getOrDefault(key, 0), rng);
                spawned++;
            }
            dustRingCursor.put(id, Math.floorMod(start + step, n));
        }
    }

    private static void spawnBorderDust(
            Player player,
            Location blockLoc,
            BorderStyle borderStyle,
            int segIndex,
            ThreadLocalRandom rng) {
        Color dustColor;
        float size;
        switch (borderStyle) {
            case OWN -> {
                boolean alt = (segIndex & 1) == 1;
                dustColor = alt ? Color.fromRGB(60, 220, 100) : Color.fromRGB(30, 200, 80);
                size = 1.0f;
            }
            case FOREIGN -> {
                dustColor = Color.fromRGB(235, 50, 50);
                size = 1.05f;
            }
            case SAFE -> {
                boolean alt = (segIndex & 1) == 1;
                dustColor = alt ? Color.fromRGB(255, 165, 30) : Color.fromRGB(255, 135, 0);
                size = 1.05f;
            }
            case WAR -> {
                boolean alt = (segIndex & 1) == 1;
                dustColor = alt ? Color.fromRGB(50, 50, 50) : Color.fromRGB(26, 26, 26);
                size = 1.06f;
            }
            default -> throw new IllegalStateException(String.valueOf(borderStyle));
        }
        Location particleLoc = blockLoc.clone().add(
                0.5 + (rng.nextDouble() - 0.5) * 0.12,
                1.08 + rng.nextDouble() * 1,
                0.5 + (rng.nextDouble() - 0.5) * 0.12
        );
        player.spawnParticle(
                Particle.DUST,
                particleLoc,
                5,
                0.04,
                0.05,
                0.04,
                0,
                new Particle.DustOptions(dustColor, size),
                true
        );
    }

    private void revertAll(Player player) {
        UUID id = player.getUniqueId();
        Map<String, Location> m = activeFakes.remove(id);
        activeStyles.remove(id);
        if (m == null || m.isEmpty()) {
            return;
        }
        for (Location loc : m.values()) {
            revertOne(player, loc);
        }
    }

    private void revertOne(Player player, Location loc) {
        World world = loc.getWorld();
        if (world == null || !world.equals(player.getWorld())) {
            return;
        }
        if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return;
        }
        BlockData real = world.getBlockAt(loc).getBlockData();
        player.sendBlockChange(loc, real);
    }

    private void pruneActiveFakes(Player player, Map<String, BorderStyle> merged) {
        UUID id = player.getUniqueId();
        ConcurrentHashMap<String, Location> m = activeFakes.get(id);
        if (m == null || m.isEmpty()) {
            return;
        }
        World pw = player.getWorld();
        ConcurrentHashMap<String, BorderStyle> styles = activeStyles.get(id);
        Iterator<Map.Entry<String, Location>> it = m.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Location> e = it.next();
            Location loc = e.getValue();
            String key = e.getKey();
            if (!loc.getWorld().equals(pw) || !merged.containsKey(key)) {
                revertOne(player, loc);
                it.remove();
                if (styles != null) {
                    styles.remove(key);
                }
            }
        }
    }

    private record DesiredRing(Map<String, BorderStyle> merged, Map<String, Integer> keyToIndex, List<String> keys) {}

    private DesiredRing computeDesiredRing(Player player) {
        World world = player.getWorld();
        UUID worldId = world.getUID();
        double px = player.getLocation().getX();
        double pz = player.getLocation().getZ();
        int band = config.borderBandBlocks();
        int viewR = config.borderViewRadius();
        int segments = config.borderSegments();
        double viewR2 = (double) viewR * viewR;

        Map<String, BorderStyle> merged = new HashMap<>();
        Map<String, Integer> keyToIndex = new HashMap<>();

        for (LandClaim claim : claims.claimsInWorld(worldId)) {
            int cx = claim.centerBlockX();
            int cz = claim.centerBlockZ();
            int r = claim.radiusBlocks();
            double dx = px - (cx + 0.5);
            double dz = pz - (cz + 0.5);
            double d = Math.sqrt(dx * dx + dz * dz);
            double edgeDist = Math.abs(d - r);
            if (edgeDist > band) {
                continue;
            }
            BorderStyle contribution = styleFor(player, claim);
            double step = 2 * Math.PI / segments;
            for (int i = 0; i < segments; i++) {
                double theta = i * step;
                int x = (int) Math.floor(cx + 0.5 + r * Math.cos(theta));
                int z = (int) Math.floor(cz + 0.5 + r * Math.sin(theta));
                double sdx = px - (x + 0.5);
                double sdz = pz - (z + 0.5);
                if (sdx * sdx + sdz * sdz > viewR2) {
                    continue;
                }
                String key = x + ":" + z;
                merged.merge(key, contribution, BorderStyle::merge);
                keyToIndex.putIfAbsent(key, i);
            }
        }

        List<String> keys = new ArrayList<>(merged.keySet());
        keys.sort(Comparator
                .comparingInt((String k) -> keyToIndex.getOrDefault(k, 0))
                .thenComparing(k -> k));
        return new DesiredRing(merged, keyToIndex, keys);
    }

    private static BlockData woolFor(BorderStyle s) {
        return switch (s) {
            case OWN -> LIME_WOOL;
            case FOREIGN -> RED_WOOL;
            case SAFE -> ORANGE_WOOL;
            case WAR -> BLACK_WOOL;
        };
    }

    private void emitRingBatch(Player player, DesiredRing ring) {
        World world = player.getWorld();
        Map<String, BorderStyle> merged = ring.merged();
        List<String> keys = ring.keys();
        int maxUpdates = config.borderMaxUpdatesPerTick();
        UUID playerId = player.getUniqueId();

        UUID worldId = world.getUID();
        UUID prevWorld = ringWorld.put(playerId, worldId);
        if (prevWorld != null && !prevWorld.equals(worldId)) {
            ringCursor.put(playerId, 0);
            dustRingCursor.put(playerId, 0);
        }

        int n = keys.size();
        if (n == 0) {
            return;
        }

        ConcurrentHashMap<String, Location> fakes = activeFakes.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        ConcurrentHashMap<String, BorderStyle> styles = activeStyles.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        int start = Math.floorMod(ringCursor.getOrDefault(playerId, 0), n);
        int sends = 0;
        int steps = 0;

        while (sends < maxUpdates && steps < n) {
            String key = keys.get(Math.floorMod(start + steps, n));
            steps++;
            BorderStyle borderStyle = merged.get(key);
            if (borderStyle == null) {
                continue;
            }
            int colon = key.indexOf(':');
            int x = Integer.parseInt(key.substring(0, colon));
            int z = Integer.parseInt(key.substring(colon + 1));
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = world.getHighestBlockYAt(x, z);
            Location blockLoc = new Location(world, x, y, z);

            Location existing = fakes.get(key);
            if (existing != null) {
                BorderStyle was = styles.get(key);
                if (Objects.equals(was, borderStyle)) {
                    continue;
                }
            }

            BlockData wool = woolFor(borderStyle);
            player.sendBlockChange(blockLoc, wool);
            fakes.put(key, blockLoc.clone());
            styles.put(key, borderStyle);
            sends++;
        }
        ringCursor.put(playerId, Math.floorMod(start + steps, n));
    }
}
