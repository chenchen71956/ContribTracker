package com.example.contribtracker.database;

import com.example.contribtracker.ContribTrackerMod;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.sqlite.SQLiteConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.atomic.AtomicBoolean;

public class DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);
    private static File dbFile;
    private static HikariDataSource dataSource;
    private static String connectionUrl;
    
    // 查询缓存系统
    private static final Map<String, List<Contribution>> contributionCache = new ConcurrentHashMap<>();
    private static final Map<Integer, Contribution> contributionByIdCache = new ConcurrentHashMap<>(); 
    private static final Map<String, Long> lastCacheUpdateTime = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRE_TIME = 5000; // 缓存过期时间：5秒
    // 状态追踪
    private static final AtomicBoolean isInitialized = new AtomicBoolean(false);

    public static void initialize() throws SQLException {
        try {
            // 确保目录存在
            File configDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "null_city/contributions");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            
            // 设置数据库文件
            dbFile = new File(configDir, "contributions.db");
            connectionUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            
            // 配置连接池
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(connectionUrl);
            config.setDriverClassName("org.sqlite.JDBC");
            
            // 配置连接池参数
            config.setMaximumPoolSize(5); // 最大连接数
            config.setMinimumIdle(1); // 最小空闲连接数
            config.setIdleTimeout(TimeUnit.MINUTES.toMillis(10)); // 空闲连接超时
            config.setMaxLifetime(TimeUnit.MINUTES.toMillis(30)); // 连接最大生命周期
            config.setConnectionTimeout(TimeUnit.SECONDS.toMillis(30)); // 连接超时
            
            // SQLite 特定配置
            SQLiteConfig sqLiteConfig = new SQLiteConfig();
            sqLiteConfig.enforceForeignKeys(true); // 启用外键约束
            sqLiteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL); // 使用WAL模式提高性能
            
            // 添加SQLite配置属性
            Properties props = sqLiteConfig.toProperties();
            props.forEach((k, v) -> config.addDataSourceProperty(k.toString(), v));
            
            // 初始化连接池
            dataSource = new HikariDataSource(config);
            
            // 创建表
            createTables();
            
            isInitialized.set(true);
        } catch (Exception e) {
            throw new SQLException("数据库初始化失败", e);
        }
    }

    /**
     * 检查数据库连接池是否已经初始化
     * @return 如果数据库连接池已初始化则返回true
     */
    public static boolean isInitialized() {
        return isInitialized.get() && dataSource != null && !dataSource.isClosed();
    }

    private static void createTables() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
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

            // 创建贡献者表 - 修复外键约束
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS contributors (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    contribution_id INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    note TEXT,
                    inviter_uuid TEXT,
                    level INTEGER DEFAULT 1,
                    FOREIGN KEY (contribution_id) REFERENCES contributions(id) ON DELETE CASCADE,
                    UNIQUE (contribution_id, player_uuid)
                )
            """);
            
            // 检查表结构是否需要升级
            upgradeTablesIfNeeded(conn);
        }
    }

    /**
     * 检查并升级表结构
     * @param conn 数据库连接
     * @throws SQLException 如果升级失败
     */
    private static void upgradeTablesIfNeeded(Connection conn) throws SQLException {
        try {
            boolean needsUpgrade = false;
            
            // 检查外键约束问题
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_list(contributors)")) {
                while (rs.next()) {
                    String table = rs.getString("table");
                    if ("contributors".equals(table)) {
                        needsUpgrade = true;
                        break;
                    }
                }
            }
            
            if (needsUpgrade) {
                // 获取当前所有数据
                List<Contribution> allContributions = new ArrayList<>();
                List<ContributorInfo> allContributors = new ArrayList<>();
                
                // 获取所有贡献和贡献者
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM contributions")) {
                    while (rs.next()) {
                        Contribution c = new Contribution();
                        c.setId(rs.getInt("id"));
                        c.setName(rs.getString("name"));
                        c.setType(rs.getString("type"));
                        c.setGameId(rs.getString("game_id"));
                        c.setX(rs.getDouble("x"));
                        c.setY(rs.getDouble("y"));
                        c.setZ(rs.getDouble("z"));
                        c.setWorld(rs.getString("world"));
                        c.setCreatorUuid(UUID.fromString(rs.getString("creator_uuid")));
                        c.setCreatedAt(rs.getTimestamp("created_at").getTime());
                        allContributions.add(c);
                    }
                }
                
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM contributors")) {
                    while (rs.next()) {
                        ContributorInfo ci = new ContributorInfo();
                        ci.setContributionId(rs.getInt("contribution_id"));
                        ci.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
                        ci.setPlayerName(rs.getString("player_name"));
                        ci.setLevel(rs.getInt("level"));
                        String inviterUuid = rs.getString("inviter_uuid");
                        if (inviterUuid != null) {
                            ci.setInviterUuid(UUID.fromString(inviterUuid));
                        }
                        allContributors.add(ci);
                    }
                }
                
                // 关闭外键约束
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = OFF");
                }
                
                // 删除旧表并创建新表
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DROP TABLE IF EXISTS contributors");
                    stmt.execute("""
                        CREATE TABLE contributors (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            contribution_id INTEGER NOT NULL,
                            player_uuid TEXT NOT NULL,
                            player_name TEXT NOT NULL,
                            note TEXT,
                            inviter_uuid TEXT,
                            level INTEGER DEFAULT 1,
                            FOREIGN KEY (contribution_id) REFERENCES contributions(id) ON DELETE CASCADE,
                            UNIQUE (contribution_id, player_uuid)
                        )
                    """);
                }
                
                // 重新插入数据
                String insertSQL = """
                    INSERT INTO contributors 
                    (contribution_id, player_uuid, player_name, note, inviter_uuid, level)
                    VALUES (?, ?, ?, ?, ?, ?)
                """;
                
                try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
                    for (ContributorInfo ci : allContributors) {
                        pstmt.setInt(1, ci.getContributionId());
                        pstmt.setString(2, ci.getPlayerUuid().toString());
                        pstmt.setString(3, ci.getPlayerName());
                        pstmt.setString(4, null); // note
                        if (ci.getInviterUuid() != null) {
                            pstmt.setString(5, ci.getInviterUuid().toString());
                        } else {
                            pstmt.setNull(5, Types.VARCHAR);
                        }
                        pstmt.setInt(6, ci.getLevel());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }
                
                // 重新启用外键约束
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON");
                }
            }
        } catch (Exception e) {
            LOGGER.error("升级数据库表结构失败", e);
            throw new SQLException("升级数据库结构失败", e);
        }
    }

    private static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("数据库连接池未初始化");
        }
        
        return dataSource.getConnection();
    }

    /**
     * 添加新贡献到数据库
     * @param name 贡献名称
     * @param type 贡献类型
     * @param gameId 游戏ID
     * @param x X坐标
     * @param y Y坐标 
     * @param z Z坐标
     * @param world 世界名称
     * @param creatorUuid 创建者UUID
     * @return 新创建贡献的ID
     * @throws SQLException 如果数据库操作失败
     */
    public static int addContribution(String name, String type, String gameId, 
                                     double x, double y, double z, String world,
                                     UUID creatorUuid) throws SQLException {
        String sql = """
            INSERT INTO contributions (name, type, game_id, x, y, z, world, creator_uuid)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, name);
            pstmt.setString(2, type);
            pstmt.setString(3, gameId);
            pstmt.setDouble(4, x);
            pstmt.setDouble(5, y);
            pstmt.setDouble(6, z);
            pstmt.setString(7, world);
            pstmt.setString(8, creatorUuid.toString());
            pstmt.executeUpdate();
            
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
        }
    }
        }
        throw new SQLException("创建贡献失败，无法获取ID");
    }
    
    /**
     * 添加贡献对象到数据库
     * @param contribution 贡献对象
     * @return 新创建贡献的ID
     * @throws SQLException 如果数据库操作失败
     */
    public static int addContribution(Contribution contribution) throws SQLException {
        return addContribution(
            contribution.getName(),
            contribution.getType(),
            contribution.getGameId(),
            contribution.getX(),
            contribution.getY(),
            contribution.getZ(),
            contribution.getWorld(),
            contribution.getCreatorUuid()
        );
    }

    /**
     * 添加贡献者
     * @param contributionId 贡献ID
     * @param playerUuid 玩家UUID
     * @param playerName 玩家名称
     * @param note 备注
     * @param inviterUuid 邀请者UUID
     * @throws SQLException 如果数据库操作失败
     */
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
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
    
    /**
     * 添加贡献者（使用ContributorInfo对象）
     * @param contributionId 贡献ID
     * @param contributor 贡献者信息对象
     * @throws SQLException 如果数据库操作失败
     */
    public static void addContributor(int contributionId, ContributorInfo contributor) throws SQLException {
        addContributor(
            contributionId, 
            contributor.getPlayerUuid(), 
            contributor.getPlayerName(), 
            null, // note
            contributor.getInviterUuid()
        );
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
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);  // 开始事务
            
            // 先删除关联的贡献者记录
            String deleteContributorsSQL = "DELETE FROM contributors WHERE contribution_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteContributorsSQL)) {
                pstmt.setInt(1, contributionId);
                int contributorsDeleted = pstmt.executeUpdate();
                LOGGER.debug("删除了{}条贡献者记录", contributorsDeleted);
            }
            
            // 再删除贡献
            String deleteContributionSQL = "DELETE FROM contributions WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteContributionSQL)) {
            pstmt.setInt(1, contributionId);
                int result = pstmt.executeUpdate();
                
                if (result > 0) {
                    // 清除相关缓存
                    contributionByIdCache.remove(contributionId);
                    contributionCache.clear();
                    lastCacheUpdateTime.clear();
                    LOGGER.debug("删除贡献并清除缓存, ID={}", contributionId);
                }
            }
            
            // 提交事务
            conn.commit();
        } catch (SQLException e) {
            // 发生错误时回滚事务
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    LOGGER.error("回滚事务失败", ex);
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);  // 恢复自动提交
                    conn.close();
                } catch (SQLException e) {
                    LOGGER.error("关闭连接失败", e);
                }
            }
        }
    }

    public static void deleteContributor(int contributionId, UUID playerUuid) throws SQLException {
        String sql = "DELETE FROM contributors WHERE contribution_id = ? AND player_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, contributionId);
            pstmt.setString(2, playerUuid.toString());
            pstmt.executeUpdate();
        }
    }

    public static Contribution getContributionById(int id) throws SQLException {
        // 检查缓存
        Contribution cachedContribution = contributionByIdCache.get(id);
        String cacheKey = "contribution_" + id;
        Long lastUpdate = lastCacheUpdateTime.get(cacheKey);
        long now = System.currentTimeMillis();
        
        if (cachedContribution != null && lastUpdate != null && now - lastUpdate < CACHE_EXPIRE_TIME) {
            LOGGER.debug("使用缓存获取贡献ID={}", id);
            return cachedContribution.copy();
        }
        
        String sql = """
            SELECT c.*, 
                   (SELECT player_name FROM contributors WHERE contribution_id = c.id AND player_uuid = c.creator_uuid) as creator_name,
                   GROUP_CONCAT(ct.player_name) as contributors
            FROM contributions c
            LEFT JOIN contributors ct ON c.id = ct.contribution_id
            WHERE c.id = ?
            GROUP BY c.id
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Contribution contribution = new Contribution();
                    setContributionFromResultSet(contribution, rs);
                    
                    // 获取贡献者列表
                    String sql2 = """
                        SELECT player_uuid, player_name, level, inviter_uuid
                        FROM contributors
                        WHERE contribution_id = ?
                        ORDER BY level ASC, player_name ASC
                    """;
                    
                    try (PreparedStatement pstmt2 = conn.prepareStatement(sql2)) {
                        pstmt2.setInt(1, id);
                        try (ResultSet rs2 = pstmt2.executeQuery()) {
                            List<ContributorInfo> contributorList = new ArrayList<>();
                            while (rs2.next()) {
                                ContributorInfo info = new ContributorInfo();
                                info.setPlayerUuid(UUID.fromString(rs2.getString("player_uuid")));
                                info.setPlayerName(rs2.getString("player_name"));
                                info.setLevel(rs2.getInt("level"));
                                String inviterUuid = rs2.getString("inviter_uuid");
                                if (inviterUuid != null) {
                                    info.setInviterUuid(UUID.fromString(inviterUuid));
                                }
                                contributorList.add(info);
                            }
                            contribution.setContributorList(contributorList);
                        }
                    }
                    
                    // 更新缓存
                    contributionByIdCache.put(id, contribution.copy());
                    lastCacheUpdateTime.put(cacheKey, System.currentTimeMillis());
                    
                    return contribution;
                }
            }
        }
        return null;
    }

    public static int getLastInsertId() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
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
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        // 检查缓存
        String cacheKey = "all_contributions";
        Long lastUpdate = lastCacheUpdateTime.get(cacheKey);
        long now = System.currentTimeMillis();
        
        if (lastUpdate != null && now - lastUpdate < CACHE_EXPIRE_TIME) {
            List<Contribution> cachedList = contributionCache.get(cacheKey);
            if (cachedList != null) {
                LOGGER.debug("使用缓存获取所有贡献，共{}条", cachedList.size());
                return new ArrayList<>(cachedList);
            }
        }
        
        List<Contribution> contributions = new ArrayList<>();
        String sql = """
            SELECT c.*, 
                   (SELECT player_name FROM contributors WHERE contribution_id = c.id AND player_uuid = c.creator_uuid) as creator_name,
                   GROUP_CONCAT(ct.player_name) as contributors
            FROM contributions c
            LEFT JOIN contributors ct ON c.id = ct.contribution_id
            GROUP BY c.id
            ORDER BY c.created_at DESC
        """;
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Contribution contribution = new Contribution();
                setContributionFromResultSet(contribution, rs);
                
                // 获取贡献者列表
                String sql2 = """
                    SELECT player_uuid, player_name, level, inviter_uuid
                    FROM contributors
                    WHERE contribution_id = ?
                    ORDER BY level ASC, player_name ASC
                """;
                
                try (PreparedStatement pstmt2 = conn.prepareStatement(sql2)) {
                    pstmt2.setInt(1, contribution.getId());
                    try (ResultSet rs2 = pstmt2.executeQuery()) {
                        List<ContributorInfo> contributorList = new ArrayList<>();
                        while (rs2.next()) {
                            ContributorInfo info = new ContributorInfo();
                            info.setPlayerUuid(UUID.fromString(rs2.getString("player_uuid")));
                            info.setPlayerName(rs2.getString("player_name"));
                            info.setLevel(rs2.getInt("level"));
                            String inviterUuid = rs2.getString("inviter_uuid");
                            if (inviterUuid != null) {
                                info.setInviterUuid(UUID.fromString(inviterUuid));
                            }
                            contributorList.add(info);
                        }
                        contribution.setContributorList(contributorList);
                    }
                }
                
                contributions.add(contribution);
            }
        }
        
        // 更新缓存
        contributionCache.put(cacheKey, new ArrayList<>(contributions));
        lastCacheUpdateTime.put(cacheKey, System.currentTimeMillis());
        
        LOGGER.debug("从数据库获取所有贡献，共{}条", contributions.size());
        return contributions;
    }

    /**
     * 获取指定贡献的贡献者数量
     * @param contributionId 贡献ID
     * @return 贡献者数量
     */
    public static int getContributorCount(int contributionId) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM contributors WHERE contribution_id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
    public static void close() throws SQLException {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        clearAllCaches();
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
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
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

    /**
     * 清除所有缓存
     */
    public static void clearAllCaches() {
        contributionCache.clear();
        contributionByIdCache.clear();
        lastCacheUpdateTime.clear();
        LOGGER.debug("已清除所有数据库查询缓存");
    }
    
    /**
     * 根据名称查找玩家信息
     * @param playerName 玩家名称
     * @return 包含匹配玩家信息的列表
     * @throws SQLException 如果查询过程中发生SQL错误
     */
    public static List<ContributorInfo> findPlayerByName(String playerName) throws SQLException {
        List<ContributorInfo> result = new ArrayList<>();
        String sql = """
            SELECT DISTINCT player_uuid, player_name
            FROM contributors
            WHERE player_name LIKE ?
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "%" + playerName + "%");
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    ContributorInfo info = new ContributorInfo();
                    info.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
                    info.setPlayerName(rs.getString("player_name"));
                    result.add(info);
                }
            }
        }
        
        return result;
    }
} 