package com.example.contribtracker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.contribtracker.database.DatabaseManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.sql.SQLException;
import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.command.CommandManager;

public class ContribTrackerMod implements ModInitializer {
    public static final String MOD_ID = "contribtracker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static MinecraftServer server;
    private static Map<UUID, Contribution> pendingContributions = new HashMap<>();
    private static Map<UUID, Long> contributionExpiryTimes = new HashMap<>();
    private static final long INVITATION_EXPIRY_TIME = 5 * 60 * 1000;
    private static boolean isDebugMode = false; // 调试模式标志，默认关闭

    @Override
    public void onInitialize() {
        LOGGER.info("初始化贡献追踪器Mod");
        
        try {
            DatabaseManager.initialize();
        } catch (SQLException e) {
            LOGGER.error("初始化数据库失败", e);
        }
        
        CommandManager.registerCommands();
        
        LOGGER.info("贡献追踪器Mod初始化完成");
        
        // 注册服务器启动和停止事件
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        
        // 添加定时任务检查过期邀请
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            long currentTime = System.currentTimeMillis();
            contributionExpiryTimes.entrySet().removeIf(entry -> {
                if (currentTime - entry.getValue() > INVITATION_EXPIRY_TIME) {
                    pendingContributions.remove(entry.getKey());
                    return true;
                }
                return false;
            });
        });
    }

    private void onServerStarting(MinecraftServer server) {
        ContribTrackerMod.server = server;
        LOGGER.info("服务器已启动");
    }

    private void onServerStopping(MinecraftServer server) {
        try {
            DatabaseManager.close();
        } catch (SQLException e) {
            LOGGER.error("关闭数据库连接失败", e);
        }
        LOGGER.info("服务器已停止");
    }
    
    public static MinecraftServer getServer() {
        return server;
    }
    
    public static Map<UUID, Contribution> getPendingContributions() {
        return pendingContributions;
    }
    
    public static Map<UUID, Long> getContributionExpiryTimes() {
        return contributionExpiryTimes;
    }
    
    /**
     * 获取当前调试模式状态
     * @return 当前调试模式是否开启
     */
    public static boolean isDebugMode() {
        return isDebugMode;
    }
    
    /**
     * 设置调试模式状态
     * @param debugMode 是否开启调试模式
     */
    public static void setDebugMode(boolean debugMode) {
        isDebugMode = debugMode;
        LOGGER.info("调试模式已" + (debugMode ? "开启" : "关闭"));
    }
    
    /**
     * 输出调试信息
     * 仅在调试模式开启时输出
     * @param message 调试信息
     */
    public static void debug(String message) {
        if (isDebugMode) {
            LOGGER.info("[DEBUG] " + message);
        }
    }
} 