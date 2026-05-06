package com.minecraftraid.persistence;

import com.minecraftraid.model.ClaimKind;
import com.minecraftraid.model.LandClaim;
import com.minecraftraid.model.RaidBlock;
import com.minecraftraid.registry.ClaimRegistry;
import com.minecraftraid.registry.RaidBlockRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Level;

/** Raid blocks in SQLite ({@link RaidBlockSqlStore}); land claims in {@code data/claims.yml} with legacy fallback. */
public final class RaidStatePersistence {

    private static ClaimKind parseClaimKind(String raw) {
        if (raw == null || raw.isEmpty()) {
            return ClaimKind.PLAYER;
        }
        try {
            return ClaimKind.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ClaimKind.PLAYER;
        }
    }

    private final JavaPlugin plugin;
    private final File dataDir;
    private final File legacyStateFile;
    private final File claimsFile;
    private final File dbFile;
    private final RaidBlockSqlStore raidBlocksSql;
    private final Object saveLock = new Object();

    public RaidStatePersistence(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), "data");
        this.legacyStateFile = new File(dataDir, "raid-state.yml");
        this.claimsFile = new File(dataDir, "claims.yml");
        this.dbFile = new File(dataDir, "raid-blocks.db");
        this.raidBlocksSql = new RaidBlockSqlStore(dbFile);
    }

    public void load(RaidBlockRegistry blocks, ClaimRegistry claims) {
        blocks.clear();
        claims.clear();
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            plugin.getLogger().warning("Could not create data directory");
            return;
        }

        try {
            raidBlocksSql.ensureSchema();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to open or initialize SQLite raid-blocks storage", e);
            return;
        }

        boolean claimsFromClaimsYaml = claimsFile.exists();
        if (claimsFromClaimsYaml) {
            loadClaimsFromYamlFile(claimsFile, claims);
        } else if (legacyStateFile.exists()) {
            loadClaimsFromLegacyFile(claims, YamlConfiguration.loadConfiguration(legacyStateFile));
        }

        boolean dbEmpty;
        try {
            dbEmpty = raidBlocksSql.isEmpty();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read raid_blocks table", e);
            return;
        }

        try {
            if (!dbEmpty) {
                loadBlocksFromSql(blocks);
            } else if (hasBlocksSection(legacyStateFile)) {
                YamlConfiguration legacyYaml = YamlConfiguration.loadConfiguration(legacyStateFile);
                migrateBlocksFromLegacyYamlSections(blocks, legacyYaml);
                raidBlocksSql.replaceAll(blocks.snapshot());
                backupLegacyStateFileAfterBlockMigration();
                saveClaimsYaml(claims.all());
                plugin.getLogger().info("Migrated raid blocks from raid-state.yml to SQLite; legacy backed up as raid-state.yml.pre-sqlite; claims written to claims.yml.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load or migrate raid blocks into SQLite", e);
        }

        // Legacy had claims but no claims.yml (and no block migration that already wrote claims.yml).
        if (!claimsFromClaimsYaml && !claims.all().isEmpty() && legacyStateFile.exists()) {
            save(java.util.Collections.emptyList(), claims.all());
            plugin.getLogger().info("Created data/claims.yml from legacy raid-state.yml (claims only).");
        }
    }

    private static boolean hasBlocksSection(File legacy) {
        if (!legacy.exists()) {
            return false;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(legacy);
        ConfigurationSection sec = y.getConfigurationSection("blocks");
        return sec != null && !sec.getKeys(false).isEmpty();
    }

    private void loadClaimsFromYamlFile(File file, ClaimRegistry claims) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        loadClaimsFromSection(claims, yaml.getConfigurationSection("claims"));
    }

    /** Uses only the claims section inside a yaml (legacy root may also have blocks). */
    private void loadClaimsFromLegacyFile(ClaimRegistry claims, YamlConfiguration root) {
        loadClaimsFromSection(claims, root.getConfigurationSection("claims"));
    }

    private void loadClaimsFromSection(ClaimRegistry claims, ConfigurationSection claimsRoot) {
        if (claimsRoot == null) {
            return;
        }
        for (String key : claimsRoot.getKeys(false)) {
            ConfigurationSection sec = claimsRoot.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            String id = sec.getString("id");
            UUID worldId = UUID.fromString(sec.getString("world", ""));
            int cx = sec.getInt("centerX");
            int cz = sec.getInt("centerZ");
            int radius = sec.getInt("radius");
            UUID owner = UUID.fromString(sec.getString("owner", ""));
            ClaimKind kind = parseClaimKind(sec.getString("kind"));
            if (id == null || Bukkit.getWorld(worldId) == null) {
                continue;
            }
            claims.add(new LandClaim(id, worldId, cx, cz, radius, owner, kind));
        }
    }

    private void loadBlocksFromSql(RaidBlockRegistry blocks) throws SQLException {
        for (RaidBlockSqlStore.SqlRaidRow row : raidBlocksSql.loadAllRows()) {
            Material mat = RaidBlockSqlStore.matchMaterialStored(row.materialName());
            World w = Bukkit.getWorld(row.worldId());
            if (w == null) {
                continue;
            }
            var blockAt = w.getBlockAt(row.x(), row.y(), row.z());
            if (blockAt.getType().isAir() || blockAt.getType() != mat) {
                plugin.getLogger().warning("Skipping stale raid block from DB at "
                        + row.worldId() + " " + row.x() + "," + row.y() + "," + row.z());
                continue;
            }
            blocks.put(new RaidBlock(
                    row.worldId(),
                    row.x(),
                    row.y(),
                    row.z(),
                    row.ownerUuid(),
                    row.maxHp(),
                    row.currentHp(),
                    mat,
                    row.breached(),
                    row.reinforcementTier()
            ));
        }
    }

    private void migrateBlocksFromLegacyYamlSections(RaidBlockRegistry blocks, YamlConfiguration yaml) {
        ConfigurationSection blocksRoot = yaml.getConfigurationSection("blocks");
        if (blocksRoot == null) {
            return;
        }
        for (String key : blocksRoot.getKeys(false)) {
            ConfigurationSection sec = blocksRoot.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            UUID worldId = UUID.fromString(sec.getString("world", ""));
            int x = sec.getInt("x");
            int y = sec.getInt("y");
            int z = sec.getInt("z");
            UUID owner = UUID.fromString(sec.getString("owner", ""));
            int maxHp = sec.getInt("maxHp");
            int currentHp = sec.getInt("currentHp");
            boolean breached = sec.getBoolean("breached");
            int reinforcementTier = sec.getInt("reinforcementTier", 0);
            reinforcementTier = Math.max(0, Math.min(3, reinforcementTier));
            Material mat = Material.matchMaterial(sec.getString("material", "STONE"));
            if (mat == null) {
                mat = Material.STONE;
            }
            World w = Bukkit.getWorld(worldId);
            if (w == null) {
                continue;
            }
            var blockAt = w.getBlockAt(x, y, z);
            if (blockAt.getType().isAir() || blockAt.getType() != mat) {
                plugin.getLogger().warning("Skipping stale raid block during YAML migration at " + worldId + " " + x + "," + y + "," + z);
                continue;
            }
            blocks.put(new RaidBlock(worldId, x, y, z, owner, maxHp, currentHp, mat, breached, reinforcementTier));
        }
    }

    private void backupLegacyStateFileAfterBlockMigration() {
        File bak = new File(dataDir, "raid-state.yml.pre-sqlite");
        try {
            Files.move(legacyStateFile.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to rename raid-state.yml after SQLite migration — remove duplicate YAML manually", e);
        }
    }

    public void save(Collection<RaidBlock> blockSnapshot, Collection<LandClaim> claimSnapshot) {
        synchronized (saveLock) {
            if (!dataDir.exists() && !dataDir.mkdirs()) {
                plugin.getLogger().warning("Could not create data directory");
                return;
            }
            try {
                raidBlocksSql.ensureSchema();
                raidBlocksSql.replaceAll(blockSnapshot);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save raid blocks to SQLite", e);
            }
            saveClaimsYaml(claimSnapshot);
        }
    }

    private void saveClaimsYaml(Collection<LandClaim> claimSnapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        ConfigurationSection claimsRoot = yaml.createSection("claims");
        int j = 0;
        for (LandClaim c : claimSnapshot) {
            ConfigurationSection s = claimsRoot.createSection(String.valueOf(j++));
            s.set("id", c.id());
            s.set("world", c.worldId().toString());
            s.set("centerX", c.centerBlockX());
            s.set("centerZ", c.centerBlockZ());
            s.set("radius", c.radiusBlocks());
            s.set("owner", c.ownerUuid().toString());
            s.set("kind", c.kind().name());
        }
        File tmp = new File(dataDir, "claims.yml.tmp");
        try {
            yaml.save(tmp);
            Files.move(tmp.toPath(), claimsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save claims.yml", e);
            try {
                Files.deleteIfExists(tmp.toPath());
            } catch (IOException ignored) {
            }
        }
    }
}
