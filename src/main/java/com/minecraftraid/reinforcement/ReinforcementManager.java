package com.minecraftraid.reinforcement;

import com.minecraftraid.config.RaidConfig;
import com.minecraftraid.model.RaidBlock;
import com.minecraftraid.registry.ClaimRegistry;
import com.minecraftraid.registry.RaidBlockRegistry;
import com.minecraftraid.util.ExperienceUtil;
import com.minecraftraid.util.Messages;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReinforcementManager {

    private final JavaPlugin plugin;
    private final RaidConfig config;
    private final RaidBlockRegistry blocks;
    private final ClaimRegistry claims;

    private final Map<UUID, Location> corner1 = new ConcurrentHashMap<>();
    private final Map<UUID, Cuboid> visualCuboid = new ConcurrentHashMap<>();
    private final Map<UUID, ReinforcementSession> sessions = new ConcurrentHashMap<>();
    /** Player -> active session id (only one pending UI per player). */
    private final Map<UUID, UUID> playerSession = new ConcurrentHashMap<>();

    private BukkitTask particleTask;
    private int sweepCounter;

    public ReinforcementManager(JavaPlugin plugin, RaidConfig config, RaidBlockRegistry blocks, ClaimRegistry claims) {
        this.plugin = plugin;
        this.config = config;
        this.blocks = blocks;
        this.claims = claims;
    }

    public void shutdown() {
        stopParticleTaskIfIdle();
        corner1.clear();
        visualCuboid.clear();
        sessions.clear();
        playerSession.clear();
    }

    public void onReload() {
        shutdown();
    }

    public void onPlayerQuit(Player player) {
        UUID id = player.getUniqueId();
        cancelPendingForPlayer(id, false);
        corner1.remove(id);
        visualCuboid.remove(id);
    }

    private void cancelPendingForPlayer(UUID playerId, boolean notify) {
        UUID sid = playerSession.remove(playerId);
        if (sid != null) {
            sessions.remove(sid);
        }
        visualCuboid.remove(playerId);
        stopParticleTaskIfIdle();
        if (notify) {
            Player p = plugin.getServer().getPlayer(playerId);
            if (p != null && p.isOnline()) {
                Messages.send(config, p, "reinforce-cancelled");
            }
        }
    }

    /**
     * Shift-left with repair tool on a raid block: first click sets corner 1, second completes cuboid.
     */
    public void handleSelectCorner(Player player, Block block) {
        if (!player.hasPermission("minecraftraid.reinforce")) {
            return;
        }
        RaidConfig cfg = config;
        Location loc = block.getLocation();
        if (!claims.isOwnerLocation(player, loc)) {
            return;
        }
        RaidBlock rb = blocks.get(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (rb == null || rb.breached() || !player.getUniqueId().equals(rb.ownerUuid())) {
            Messages.send(config, player, "reinforce-not-owned-raid");
            return;
        }

        Location first = corner1.get(player.getUniqueId());
        if (first == null || !first.getWorld().getUID().equals(loc.getWorld().getUID())) {
            corner1.put(player.getUniqueId(), loc.clone());
            Messages.send(config, player, "reinforce-corner1");
            stopParticleTaskIfIdle();
            return;
        }

        corner1.remove(player.getUniqueId());
        int x1 = first.getBlockX();
        int y1 = first.getBlockY();
        int z1 = first.getBlockZ();
        int x2 = loc.getBlockX();
        int y2 = loc.getBlockY();
        int z2 = loc.getBlockZ();
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > cfg.reinforcementMaxBlocksPerSelection()) {
            Messages.send(config, player, "reinforce-too-many-blocks");
            return;
        }

        World world = loc.getWorld();
        List<ReinforcementTarget> targets = new ArrayList<>();
        int c1 = 0;
        int c2 = 0;
        int c3 = 0;
        int totalXp = 0;
        int baseXp = cfg.reinforcementXpBasePerBlock();
        int xpPerT = cfg.reinforcementXpPerBlockPerTargetTier();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    RaidBlock b = blocks.get(world, x, y, z);
                    if (b == null || b.breached()) {
                        continue;
                    }
                    if (!player.getUniqueId().equals(b.ownerUuid())) {
                        continue;
                    }
                    Location L = new Location(world, x, y, z);
                    if (!claims.isOwnerLocation(player, L)) {
                        continue;
                    }
                    int tier = b.reinforcementTier();
                    if (tier >= RaidBlock.MAX_REINFORCEMENT_TIER) {
                        continue;
                    }
                    int next = tier + 1;
                    targets.add(new ReinforcementTarget(x, y, z, tier));
                    totalXp += baseXp + xpPerT * next;
                    if (tier == 0) {
                        c1++;
                    } else if (tier == 1) {
                        c2++;
                    } else {
                        c3++;
                    }
                }
            }
        }

        if (targets.isEmpty()) {
            Messages.send(config, player, "reinforce-no-blocks");
            return;
        }

        cancelPendingForPlayer(player.getUniqueId(), false);
        UUID sessionId = UUID.randomUUID();
        long deadline = System.currentTimeMillis() + cfg.reinforcementSessionTimeoutTicks() * 50L;
        int hpSnap = cfg.reinforcementHpPerTier();
        ReinforcementSession session = new ReinforcementSession(
                sessionId,
                player.getUniqueId(),
                world.getUID(),
                List.copyOf(targets),
                c1,
                c2,
                c3,
                totalXp,
                hpSnap,
                deadline
        );
        sessions.put(sessionId, session);
        playerSession.put(player.getUniqueId(), sessionId);
        visualCuboid.put(player.getUniqueId(), new Cuboid(world, minX, minY, minZ, maxX, maxY, maxZ));
        ensureParticleTask();

        sendPreview(player, session);
    }

    private void sendPreview(Player player, ReinforcementSession s) {
        if (s.countMatTier1() > 0) {
            Map<String, String> ph = new HashMap<>();
            ph.put("count", String.valueOf(s.countMatTier1()));
            ph.put("material", formatMaterial(config.reinforcementTier1Material()));
            Messages.sendMiniBody(player, config, "reinforce-preview-tier1", ph);
        }
        if (s.countMatTier2() > 0) {
            Map<String, String> ph = new HashMap<>();
            ph.put("count", String.valueOf(s.countMatTier2()));
            ph.put("material", formatMaterial(config.reinforcementTier2Material()));
            Messages.sendMiniBody(player, config, "reinforce-preview-tier2", ph);
        }
        if (s.countMatTier3() > 0) {
            Map<String, String> ph = new HashMap<>();
            ph.put("count", String.valueOf(s.countMatTier3()));
            ph.put("material", formatMaterial(config.reinforcementTier3Material()));
            Messages.sendMiniBody(player, config, "reinforce-preview-tier3", ph);
        }
        Map<String, String> px = new HashMap<>();
        px.put("xp", String.valueOf(s.totalXp()));
        Messages.sendMiniBody(player, config, "reinforce-preview-xp", px);
        Map<String, String> pa = new HashMap<>();
        pa.put("session", s.sessionId().toString());
        Messages.sendMiniBody(player, config, "reinforce-accept-deny", pa);
    }

    public boolean handleConfirm(Player player, UUID sessionId) {
        if (!player.hasPermission("minecraftraid.reinforce")) {
            return true;
        }
        ReinforcementSession session = sessions.get(sessionId);
        if (session == null || !session.ownerUuid().equals(player.getUniqueId())) {
            Messages.send(config, player, "reinforce-session-gone");
            return true;
        }
        if (System.currentTimeMillis() > session.deadlineMillis()) {
            cancelPendingForPlayer(player.getUniqueId(), false);
            sessions.remove(sessionId);
            Messages.send(config, player, "reinforce-session-expired");
            return true;
        }

        World world = plugin.getServer().getWorld(session.worldId());
        if (world == null) {
            cancelPendingForPlayer(player.getUniqueId(), false);
            sessions.remove(sessionId);
            Messages.send(config, player, "reinforce-session-gone");
            return true;
        }

        Material m1 = config.reinforcementTier1Material();
        Material m2 = config.reinforcementTier2Material();
        Material m3 = config.reinforcementTier3Material();
        if (countMaterial(player, m1) < session.countMatTier1()
                || countMaterial(player, m2) < session.countMatTier2()
                || countMaterial(player, m3) < session.countMatTier3()) {
            Messages.send(config, player, "reinforce-not-enough-items");
            return true;
        }
        if (ExperienceUtil.totalPoints(player) < session.totalXp()) {
            Messages.send(config, player, "reinforce-not-enough-xp");
            return true;
        }

        List<RaidBlock> snap = new ArrayList<>();
        for (ReinforcementTarget t : session.targets()) {
            RaidBlock rb = blocks.get(world, t.x(), t.y(), t.z());
            if (rb == null || rb.breached() || rb.reinforcementTier() != t.fromTier()
                    || !rb.ownerUuid().equals(player.getUniqueId())) {
                cancelPendingForPlayer(player.getUniqueId(), false);
                sessions.remove(sessionId);
                Messages.send(config, player, "reinforce-session-invalid");
                return true;
            }
            snap.add(rb);
        }

        if (!removeItems(player, m1, session.countMatTier1())
                || !removeItems(player, m2, session.countMatTier2())
                || !removeItems(player, m3, session.countMatTier3())) {
            Messages.send(config, player, "reinforce-not-enough-items");
            return true;
        }
        if (!ExperienceUtil.tryRemove(player, session.totalXp())) {
            restoreItems(player, m1, session.countMatTier1());
            restoreItems(player, m2, session.countMatTier2());
            restoreItems(player, m3, session.countMatTier3());
            Messages.send(config, player, "reinforce-not-enough-xp");
            return true;
        }

        int hp = session.hpPerTierSnapshot();
        for (RaidBlock rb : snap) {
            blocks.replace(rb.withAppliedReinforcement(hp));
        }

        playerSession.remove(player.getUniqueId());
        sessions.remove(sessionId);
        visualCuboid.remove(player.getUniqueId());
        stopParticleTaskIfIdle();

        Messages.send(config, player, "reinforce-success");
        playRainbow(player, world, session.targets());
        return true;
    }

    public boolean handleDeny(Player player, UUID sessionId) {
        ReinforcementSession s = sessions.remove(sessionId);
        if (s == null || !s.ownerUuid().equals(player.getUniqueId())) {
            Messages.send(config, player, "reinforce-session-gone");
            return true;
        }
        playerSession.remove(player.getUniqueId());
        visualCuboid.remove(player.getUniqueId());
        stopParticleTaskIfIdle();
        Messages.send(config, player, "reinforce-denied");
        return true;
    }

    private static boolean removeItems(Player player, Material mat, int amount) {
        if (amount <= 0) {
            return true;
        }
        return player.getInventory().removeItem(new ItemStack(mat, amount)).isEmpty();
    }

    private static void restoreItems(Player player, Material mat, int amount) {
        if (amount <= 0) {
            return;
        }
        player.getInventory().addItem(new ItemStack(mat, amount));
    }

    private static int countMaterial(Player player, Material mat) {
        int n = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && it.getType() == mat) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private void playRainbow(Player viewer, World world, List<ReinforcementTarget> targets) {
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (tick++ >= 16) {
                    cancel();
                    return;
                }
                float hue = (tick * 22f) % 360f;
                java.awt.Color awt = java.awt.Color.getHSBColor(hue / 360f, 1f, 1f);
                Color dust = Color.fromRGB(awt.getRed(), awt.getGreen(), awt.getBlue());
                Particle.DustOptions opts = new Particle.DustOptions(dust, 1.05f);
                for (ReinforcementTarget t : targets) {
                    spawnWireframeCuboid(viewer, world, opts, t.x(), t.y(), t.z(), t.x(), t.y(), t.z());
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void ensureParticleTask() {
        if (particleTask != null) {
            return;
        }
        particleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (visualCuboid.isEmpty()) {
                stopParticleTaskIfIdle();
                return;
            }
            sweepCounter++;
            if (sweepCounter % 20 == 0) {
                sweepExpiredSessions();
            }
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Cuboid> e : visualCuboid.entrySet()) {
                UUID playerId = e.getKey();
                Player p = plugin.getServer().getPlayer(playerId);
                if (p == null || !p.isOnline()) {
                    continue;
                }
                UUID activeSid = playerSession.get(playerId);
                if (activeSid == null) {
                    continue;
                }
                ReinforcementSession s = sessions.get(activeSid);
                if (s != null && now > s.deadlineMillis()) {
                    UUID sid = playerSession.remove(playerId);
                    sessions.remove(sid);
                    visualCuboid.remove(playerId);
                    Messages.send(config, p, "reinforce-session-expired");
                    stopParticleTaskIfIdle();
                    continue;
                }
                Cuboid box = e.getValue();
                spawnWireframeYellow(p, box);
            }
        }, 1L, 1L);
    }

    private void sweepExpiredSessions() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, UUID> e : new HashMap<>(playerSession).entrySet()) {
            ReinforcementSession s = sessions.get(e.getValue());
            if (s != null && now > s.deadlineMillis()) {
                UUID pid = e.getKey();
                sessions.remove(e.getValue());
                playerSession.remove(pid);
                visualCuboid.remove(pid);
                Player p = plugin.getServer().getPlayer(pid);
                if (p != null && p.isOnline()) {
                    Messages.send(config, p, "reinforce-session-expired");
                }
            }
        }
    }

    private void stopParticleTaskIfIdle() {
        if (visualCuboid.isEmpty() && particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
    }

    /** Nudge outward from block voxel bounds so dust sits in air, not occluded inside solids. */
    private static final double OUTLINE_OUTSET = 0.08;
    /** Target spacing along each edge (blocks) for a continuous-looking line. */
    private static final double OUTLINE_STEP = 0.14;

    private static void spawnWireframeYellow(Player viewer, Cuboid c) {
        Particle.DustOptions opts = new Particle.DustOptions(Color.YELLOW, 0.9f);
        spawnWireframeCuboid(viewer, c.world(), opts, c.minX(), c.minY(), c.minZ(), c.maxX(), c.maxY(), c.maxZ());
    }

    /**
     * Selection line and per-block success burst: axis-aligned wireframe on the outer shell of
     * an inclusive block box [min..max], outset slightly into air (same as selection).
     */
    private static void spawnWireframeCuboid(Player viewer, World w, Particle.DustOptions opts,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        double e = OUTLINE_OUTSET;
        double x0 = minX - e;
        double x1 = maxX + 1.0 + e;
        double y0 = minY - e;
        double y1 = maxY + 1.0 + e;
        double z0 = minZ - e;
        double z1 = maxZ + 1.0 + e;

        // Bottom face (y = y0)
        edgeDust(viewer, w, x0, y0, z0, x1, y0, z0, opts);
        edgeDust(viewer, w, x0, y0, z1, x1, y0, z1, opts);
        edgeDust(viewer, w, x0, y0, z0, x0, y0, z1, opts);
        edgeDust(viewer, w, x1, y0, z0, x1, y0, z1, opts);
        // Top face (y = y1)
        edgeDust(viewer, w, x0, y1, z0, x1, y1, z0, opts);
        edgeDust(viewer, w, x0, y1, z1, x1, y1, z1, opts);
        edgeDust(viewer, w, x0, y1, z0, x0, y1, z1, opts);
        edgeDust(viewer, w, x1, y1, z0, x1, y1, z1, opts);
        // Vertical edges
        edgeDust(viewer, w, x0, y0, z0, x0, y1, z0, opts);
        edgeDust(viewer, w, x1, y0, z0, x1, y1, z0, opts);
        edgeDust(viewer, w, x0, y0, z1, x0, y1, z1, opts);
        edgeDust(viewer, w, x1, y0, z1, x1, y1, z1, opts);
    }

    private static void edgeDust(Player viewer, World w, double x1, double y1, double z1,
            double x2, double y2, double z2, Particle.DustOptions opts) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(2, (int) Math.ceil(len / OUTLINE_STEP));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = x1 + dx * t;
            double y = y1 + dy * t;
            double z = z1 + dz * t;
            viewer.spawnParticle(Particle.DUST, new Location(w, x, y, z), 1, 0, 0, 0, 0, opts, true);
        }
    }

    private static String formatMaterial(Material m) {
        String n = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
