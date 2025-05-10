package com.example.contribtracker.database;

import com.example.contribtracker.ContribTrackerMod;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);
    private static Connection connection;
    private static final String DB_NAME = "contributions.db";

    public static void initialize() throws SQLException {
        File configDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "null_city/contributions");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        String dbPath = new File(configDir, DB_NAME).getAbsolutePath();
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
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
                    creator_uuid TEXT NOT NULL,
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
                    inviter_uuid TEXT,
                    level INTEGER DEFAULT 1,
                    FOREIGN KEY (contribution_id) REFERENCES contributions(id),
                    FOREIGN KEY (inviter_uuid) REFERENCES contributors(player_uuid)
                )
            """);
        }
    }

    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_NAME);
        }
        return connection;
    }

    public static void addContribution(String name, String type, String gameId, 
                                     double x, double y, double z, String world,
                                     UUID creatorUuid) throws SQLException {
        String sql = """
            INSERT INTO contributions (name, type, game_id, x, y, z, world, creator_uuid)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, name);
            pstmt.setString(2, type);
            pstmt.setString(3, gameId);
            pstmt.setDouble(4, x);
            pstmt.setDouble(5, y);
            pstmt.setDouble(6, z);
            pstmt.setString(7, world);
            pstmt.setString(8, creatorUuid.toString());
            pstmt.executeUpdate();
        }
    }

    public static void addContributor(int contributionId, UUID playerUuid, String playerName, String note, UUID inviterUuid) 
            throws SQLException {
        String sql = """
            INSERT INTO contributors (contribution_id, player_uuid, player_name, note, inviter_uuid, level)
            VALUES (?, ?, ?, ?, ?, 
                CASE 
                    WHEN ? IS NULL THEN 1
                    ELSE (SELECT level + 1 FROM contributors WHERE contribution_id = ? AND player_uuid = ?)
                END
            )
        """;
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, contributionId);
            pstmt.setString(2, playerUuid.toString());
            pstmt.setString(3, playerName);
            pstmt.setString(4, note);
            pstmt.setString(5, inviterUuid != null ? inviterUuid.toString() : null);
            pstmt.setString(6, inviterUuid != null ? inviterUuid.toString() : null);
            pstmt.setInt(7, contributionId);
            pstmt.setString(8, inviterUuid != null ? inviterUuid.toString() : null);
            pstmt.executeUpdate();
        }
    }

    public static List<Contribution> getNearbyContributions(double x, double y, double z, double radius) 
            throws SQLException {
        List<Contribution> contributions = new ArrayList<>();
        String sql = """
            SELECT c.*, GROUP_CONCAT(ct.player_name) as contributors,
                   (SELECT player_name FROM contributors WHERE contribution_id = c.id AND player_uuid = c.creator_uuid) as creator_name
            FROM contributions c
            LEFT JOIN contributors ct ON c.id = ct.contribution_id
            WHERE ABS(c.x - ?) <= ? AND ABS(c.y - ?) <= ? AND ABS(c.z - ?) <= ?
            GROUP BY c.id
        """;
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setDouble(1, x);
            pstmt.setDouble(2, radius);
            pstmt.setDouble(3, y);
            pstmt.setDouble(4, radius);
            pstmt.setDouble(5, z);
            pstmt.setDouble(6, radius);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Contribution contribution = new Contribution();
                    setContributionFromResultSet(contribution, rs);
                    contributions.add(contribution);
                }
            }
        }
        
        return contributions;
    }

    public static void deleteContribution(int contributionId) throws SQLException {
        String sql = "DELETE FROM contributions WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, contributionId);
            pstmt.executeUpdate();
        }
    }

    public static void deleteContributor(int contributionId, UUID playerUuid) throws SQLException {
        String sql = "DELETE FROM contributors WHERE contribution_id = ? AND player_uuid = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, contributionId);
            pstmt.setString(2, playerUuid.toString());
            pstmt.executeUpdate();
        }
    }

    public static Contribution getContributionById(int id) throws SQLException {
        String sql = """
            SELECT c.*, GROUP_CONCAT(ct.player_name) as contributors,
                   (SELECT player_name FROM contributors WHERE contribution_id = c.id AND player_uuid = c.creator_uuid) as creator_name
            FROM contributions c
            LEFT JOIN contributors ct ON c.id = ct.contribution_id
            WHERE c.id = ?
            GROUP BY c.id
        """;
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Contribution contribution = new Contribution();
                    setContributionFromResultSet(contribution, rs);
                    return contribution;
                }
            }
        }
        return null;
    }

    public static int getLastInsertId() throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("无法获取最后插入的ID");
    }

    public static boolean canManageContributor(int contributionId, UUID managerUuid, UUID targetUuid) throws SQLException {
        String sql = """
            SELECT c1.level as manager_level, c2.level as target_level
            FROM contributors c1
            JOIN contributors c2 ON c1.contribution_id = c2.contribution_id
            WHERE c1.contribution_id = ? AND c1.player_uuid = ? AND c2.player_uuid = ?
        """;
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, contributionId);
            pstmt.setString(2, managerUuid.toString());
            pstmt.setString(3, targetUuid.toString());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int managerLevel = rs.getInt("manager_level");
                    int targetLevel = rs.getInt("target_level");
                    return managerLevel < targetLevel;
                }
            }
        }
        return false;
    }

    /**
     * 检查玩家是否是一级贡献者
     * @param contributionId 贡献ID
     * @param playerUuid 玩家UUID
     * @return true如果玩家是一级贡献者
     */
    public static boolean isLevelOneContributor(int contributionId, UUID playerUuid) throws SQLException {
        String sql = """
            SELECT level
            FROM contributors
            WHERE contribution_id = ? AND player_uuid = ?
        """;
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, contributionId);
            pstmt.setString(2, playerUuid.toString());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int level = rs.getInt("level");
                    return level == 1;
                }
            }
        }
        return false;
    }

    /**
     * 获取贡献者的上级信息
     * @param contributionId 贡献ID
     * @param playerUuid 玩家UUID
     * @return 上级贡献者的信息，如果没有上级则返回null
     */
    public static ContributorInfo getContributorSuperior(int contributionId, UUID playerUuid) throws SQLException {
        String sql = """
            SELECT c.player_uuid, c.player_name, c.level
            FROM contributors c
            WHERE c.contribution_id = ? AND c.player_uuid = (
                SELECT inviter_uuid 
                FROM contributors 
                WHERE contribution_id = ? AND player_uuid = ?
            )
        """;
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, contributionId);
            pstmt.setInt(2, contributionId);
            pstmt.setString(3, playerUuid.toString());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    ContributorInfo superior = new ContributorInfo();
                    superior.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
                    superior.setPlayerName(rs.getString("player_name"));
                    superior.setLevel(rs.getInt("level"));
                    return superior;
                }
            }
        }
        return null;
    }

    /**
     * 检查玩家是否已经是贡献者
     * @param contributionId 贡献ID
     * @param playerUuid 玩家UUID
     * @return 如果是贡献者，返回贡献者信息；否则返回null
     */
    public static ContributorInfo getContributorInfo(int contributionId, UUID playerUuid) throws SQLException {
        String sql = """
            SELECT player_uuid, player_name, level, inviter_uuid
            FROM contributors
            WHERE contribution_id = ? AND player_uuid = ?
        """;
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, contributionId);
            pstmt.setString(2, playerUuid.toString());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    ContributorInfo info = new ContributorInfo();
                    info.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
                    info.setPlayerName(rs.getString("player_name"));
                    info.setLevel(rs.getInt("level"));
                    String inviterUuid = rs.getString("inviter_uuid");
                    if (inviterUuid != null) {
                        info.setInviterUuid(UUID.fromString(inviterUuid));
                    }
                    return info;
                }
            }
        }
        return null;
    }

    /**
     * 获取指定贡献的所有贡献者信息
     * @param contributionId 贡献ID
     * @return 贡献者信息列表
     */
    public static List<ContributorInfo> getContributorsByContributionId(int contributionId) throws SQLException {
        List<ContributorInfo> contributors = new ArrayList<>();
        String sql = """
            SELECT player_uuid, player_name, level, inviter_uuid
            FROM contributors
            WHERE contribution_id = ?
            ORDER BY level ASC, player_name ASC
        """;
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, contributionId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    ContributorInfo info = new ContributorInfo();
                    info.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
                    info.setPlayerName(rs.getString("player_name"));
                    info.setLevel(rs.getInt("level"));
                    String inviterUuid = rs.getString("inviter_uuid");
                    if (inviterUuid != null) {
                        info.setInviterUuid(UUID.fromString(inviterUuid));
                    }
                    contributors.add(info);
                }
            }
        }
        
        return contributors;
    }

    /**
     * 获取所有贡献信息
     * @return 贡献列表
     */
    public static List<Contribution> getAllContributions() throws SQLException {
        List<Contribution> contributions = new ArrayList<>();
        String sql = """
            SELECT c.*, GROUP_CONCAT(ct.player_name) as contributors,
                   (SELECT player_name FROM contributors WHERE contribution_id = c.id AND player_uuid = c.creator_uuid) as creator_name
            FROM contributions c
            LEFT JOIN contributors ct ON c.id = ct.contribution_id
            GROUP BY c.id
            ORDER BY c.created_at DESC
        """;
        
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Contribution contribution = new Contribution();
                setContributionFromResultSet(contribution, rs);
                contributions.add(contribution);
            }
        }
        
        return contributions;
    }

    /**
     * 获取指定贡献的贡献者数量
     * @param contributionId 贡献ID
     * @return 贡献者数量
     */
    public static int getContributorCount(int contributionId) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM contributors WHERE contribution_id = ?";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, contributionId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        }
        
        return 0;
    }

    /**
     * 关闭数据库连接
     * @throws SQLException 如果关闭连接时发生错误
     */
    public static synchronized void close() throws SQLException {
        if (connection != null) {
            try {
            connection.close();
                LOGGER.info("数据库连接已关闭");
            } finally {
                connection = null; // 确保连接被释放
            }
        }
    }

    /**
     * 检查玩家是否是贡献者
     * @param contributionId 贡献ID
     * @param playerUuid 玩家UUID
     * @return true如果玩家是该贡献的贡献者
     */
    public static boolean isContributor(int contributionId, UUID playerUuid) {
        try {
            String sql = "SELECT 1 FROM contributors WHERE contribution_id = ? AND player_uuid = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setInt(1, contributionId);
                pstmt.setString(2, playerUuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next(); // 如果有结果，则是贡献者
                }
            }
        } catch (SQLException e) {
            LOGGER.error("检查贡献者失败", e);
            return false;
        }
    }

    /**
     * 根据名称获取贡献
     * @param name 贡献名称
     * @return 贡献对象，不存在则返回null
     */
    public static Contribution getContributionByName(String name) {
        try {
            String sql = """
                SELECT c.*, GROUP_CONCAT(ct.player_name) as contributors,
                       (SELECT player_name FROM contributors WHERE contribution_id = c.id AND player_uuid = c.creator_uuid) as creator_name
                FROM contributions c
                LEFT JOIN contributors ct ON c.id = ct.contribution_id
                WHERE c.name = ?
                GROUP BY c.id
            """;
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setString(1, name);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        Contribution contribution = new Contribution();
                        setContributionFromResultSet(contribution, rs);
                        return contribution;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("获取贡献失败", e);
        }
        return null;
    }

    private static void setContributionFromResultSet(Contribution contribution, ResultSet rs) throws SQLException {
        contribution.setId(rs.getInt("id"));
        contribution.setName(rs.getString("name"));
        contribution.setType(rs.getString("type"));
        contribution.setGameId(rs.getString("game_id"));
        contribution.setX(rs.getDouble("x"));
        contribution.setY(rs.getDouble("y"));
        contribution.setZ(rs.getDouble("z"));
        contribution.setWorld(rs.getString("world"));
        contribution.setCreatedAt(rs.getTimestamp("created_at").getTime());
        contribution.setContributors(rs.getString("contributors"));
        contribution.setCreatorUuid(UUID.fromString(rs.getString("creator_uuid")));
        contribution.setCreatorName(rs.getString("creator_name"));
    }

    /**
     * 获取指定创建者的所有贡献，按创建时间倒序排列
     * @param creatorUuid 创建者UUID
     * @return 贡献列表
     */
    public static List<Contribution> getAllContributionsByCreator(UUID creatorUuid) throws SQLException {
        List<Contribution> contributions = new ArrayList<>();
        String sql = """
            SELECT c.*, GROUP_CONCAT(ct.player_name) as contributors,
                   (SELECT player_name FROM contributors WHERE contribution_id = c.id AND player_uuid = c.creator_uuid) as creator_name
            FROM contributions c
            LEFT JOIN contributors ct ON c.id = ct.contribution_id
            WHERE c.creator_uuid = ?
            GROUP BY c.id
            ORDER BY c.created_at DESC
        """;
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, creatorUuid.toString());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Contribution contribution = new Contribution();
                    setContributionFromResultSet(contribution, rs);
                    contributions.add(contribution);
                }
            }
        }
        
        return contributions;
    }

    /**
     * 检查玩家是否是贡献的创建者
     * @param contributionId 贡献ID
     * @param playerUuid 玩家UUID
     * @return true如果玩家是贡献的创建者，否则返回false
     */
    public static boolean isContributionCreator(int contributionId, UUID playerUuid) throws SQLException {
        String sql = """
            SELECT 1
            FROM contributions
            WHERE id = ? AND creator_uuid = ?
        """;
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, contributionId);
            pstmt.setString(2, playerUuid.toString());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next(); // 如果有记录，则玩家是创建者
            }
        }
    }

    /**
     * 根据玩家名称和贡献ID获取玩家UUID
     * @param playerName 玩家名称
     * @param contributionId 贡献ID
     * @return 如果找到，返回玩家UUID，否则返回null
     */
    public static UUID getPlayerUuidByName(String playerName, int contributionId) throws SQLException {
        String sql = """
            SELECT player_uuid
            FROM contributors
            WHERE contribution_id = ? AND player_name = ?
        """;
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, contributionId);
            pstmt.setString(2, playerName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String uuidStr = rs.getString("player_uuid");
                    return UUID.fromString(uuidStr);
                }
            }
        }
        return null;
    }
} 