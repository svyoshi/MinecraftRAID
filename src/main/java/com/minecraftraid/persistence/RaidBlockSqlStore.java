package com.minecraftraid.persistence;

import com.minecraftraid.model.RaidBlock;
import org.bukkit.Material;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * SQLite storage for raid block snapshots (full replace per save). WAL tuned for Minecraft server workloads.
 */
public final class RaidBlockSqlStore {

    private static final int BATCH_SIZE = 500;

    /** Row read from JDBC before Bukkit validation. */
    public record SqlRaidRow(
            UUID worldId,
            int x,
            int y,
            int z,
            UUID ownerUuid,
            int maxHp,
            int currentHp,
            String materialName,
            boolean breached,
            int reinforcementTier
    ) {}

    private final String jdbcUrl;

    public RaidBlockSqlStore(java.io.File dbFile) {
        this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
    }

    public void ensureSchema() throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl);
                Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode = WAL;");
            st.execute("PRAGMA synchronous = NORMAL;");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS raid_blocks (
                        world_id TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        owner_uuid TEXT NOT NULL,
                        max_hp INTEGER NOT NULL,
                        current_hp INTEGER NOT NULL,
                        material TEXT NOT NULL,
                        breached INTEGER NOT NULL,
                        reinforcement_tier INTEGER NOT NULL,
                        PRIMARY KEY (world_id, x, y, z)
                    );
                    """);
        }
    }

    public boolean isEmpty() throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl);
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT 1 AS n FROM raid_blocks LIMIT 1")) {
            return !rs.next();
        }
    }

    public List<SqlRaidRow> loadAllRows() throws SQLException {
        List<SqlRaidRow> rows = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(jdbcUrl);
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT world_id, x, y, z, owner_uuid, max_hp, current_hp,
                               material, breached, reinforcement_tier
                        FROM raid_blocks
                        ORDER BY world_id, x, y, z
                        """)) {
            while (rs.next()) {
                UUID wid = UUID.fromString(rs.getString("world_id"));
                int reinforcementTier = Math.max(0, Math.min(3, rs.getInt("reinforcement_tier")));
                rows.add(new SqlRaidRow(
                        wid,
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getInt("max_hp"),
                        rs.getInt("current_hp"),
                        rs.getString("material"),
                        rs.getInt("breached") != 0,
                        reinforcementTier
                ));
            }
        }
        return rows;
    }

    /** Full snapshot replace inside one transaction; callers should serialize overlapping saves. */
    public void replaceAll(Collection<RaidBlock> snapshot) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            // PRAGMA synchronous / journal_mode cannot run inside a transaction.
            pragmasWarm(c);
            c.setAutoCommit(false);
            try {
                try (Statement stDel = c.createStatement()) {
                    stDel.executeUpdate("DELETE FROM raid_blocks");
                }
                try (PreparedStatement insert = c.prepareStatement("""
                        INSERT INTO raid_blocks (
                          world_id, x, y, z, owner_uuid, max_hp, current_hp,
                          material, breached, reinforcement_tier
                        ) VALUES (?,?,?,?,?,?,?,?,?,?)
                        """)) {
                    int n = 0;
                    for (RaidBlock b : snapshot) {
                        insert.setString(1, b.worldId().toString());
                        insert.setInt(2, b.x());
                        insert.setInt(3, b.y());
                        insert.setInt(4, b.z());
                        insert.setString(5, b.ownerUuid().toString());
                        insert.setInt(6, b.maxHp());
                        insert.setInt(7, b.currentHp());
                        insert.setString(8, b.material().name());
                        insert.setInt(9, b.breached() ? 1 : 0);
                        insert.setInt(10, Math.max(0, Math.min(3, b.reinforcementTier())));
                        insert.addBatch();
                        if (++n % BATCH_SIZE == 0) {
                            insert.executeBatch();
                        }
                    }
                    insert.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** PRAGMAs when only replacing (caller may have run ensureSchema separately). */
    private static void pragmasWarm(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode = WAL;");
            st.execute("PRAGMA synchronous = NORMAL;");
        }
    }

    /** For validation / deserialization: match stored material name or fall back stone. */
    public static Material matchMaterialStored(String stored) {
        if (stored == null || stored.isEmpty()) {
            return Material.STONE;
        }
        Material m = Material.matchMaterial(stored);
        return m != null ? m : Material.STONE;
    }
}
