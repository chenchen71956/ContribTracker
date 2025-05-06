package com.example.contribtracker.database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:contributions.db";
    private static Connection connection;

    public static void initialize() throws SQLException {
        connection = DriverManager.getConnection(DB_URL);
        createTables();
    }

    private static void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // 创建贡献表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS contributions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    game_id TEXT,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    world TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // 创建贡献者表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS contributors (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    contribution_id INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    note TEXT,
                    FOREIGN KEY (contribution_id) REFERENCES contributions(id)
                )
            """);
        }
    }

    public static void addContribution(String name, String type, String gameId, 
                                     double x, double y, double z, String world) throws SQLException {
        String sql = """
            INSERT INTO contributions (name, type, game_id, x, y, z, world)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, name);
            pstmt.setString(2, type);
            pstmt.setString(3, gameId);
            pstmt.setDouble(4, x);
            pstmt.setDouble(5, y);
            pstmt.setDouble(6, z);
            pstmt.setString(7, world);
            pstmt.executeUpdate();
        }
    }

    public static void addContributor(int contributionId, UUID playerUuid, String playerName, String note) 
            throws SQLException {
        String sql = """
            INSERT INTO contributors (contribution_id, player_uuid, player_name, note)
            VALUES (?, ?, ?, ?)
        """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, contributionId);
            pstmt.setString(2, playerUuid.toString());
            pstmt.setString(3, playerName);
            pstmt.setString(4, note);
            pstmt.executeUpdate();
        }
    }

    public static List<Contribution> getNearbyContributions(double x, double y, double z, double radius) 
            throws SQLException {
        List<Contribution> contributions = new ArrayList<>();
        String sql = """
            SELECT c.*, GROUP_CONCAT(ct.player_name) as contributors
            FROM contributions c
            LEFT JOIN contributors ct ON c.id = ct.contribution_id
            WHERE ABS(c.x - ?) <= ? AND ABS(c.y - ?) <= ? AND ABS(c.z - ?) <= ?
            GROUP BY c.id
        """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setDouble(1, x);
            pstmt.setDouble(2, radius);
            pstmt.setDouble(3, y);
            pstmt.setDouble(4, radius);
            pstmt.setDouble(5, z);
            pstmt.setDouble(6, radius);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Contribution contribution = new Contribution();
                    contribution.setId(rs.getInt("id"));
                    contribution.setName(rs.getString("name"));
                    contribution.setType(rs.getString("type"));
                    contribution.setGameId(rs.getString("game_id"));
                    contribution.setX(rs.getDouble("x"));
                    contribution.setY(rs.getDouble("y"));
                    contribution.setZ(rs.getDouble("z"));
                    contribution.setWorld(rs.getString("world"));
                    contribution.setCreatedAt(rs.getTimestamp("created_at"));
                    contribution.setContributors(rs.getString("contributors"));
                    contributions.add(contribution);
                }
            }
        }
        
        return contributions;
    }

    public static void deleteContribution(String name) throws SQLException {
        String sql = "DELETE FROM contributions WHERE name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.executeUpdate();
        }
    }

    public static int getLastInsertId() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("无法获取最后插入的ID");
    }

    public static void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }
} 