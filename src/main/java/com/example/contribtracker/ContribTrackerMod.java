package com.example.contribtracker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.contribtracker.database.DatabaseManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.SQLException;
import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.command.AddCommand;
import com.example.contribtracker.command.DeleteCommand;
import com.example.contribtracker.command.ListCommand;
import com.example.contribtracker.command.AcceptCommand;
import com.example.contribtracker.command.RejectCommand;
import com.example.contribtracker.command.RemoveCommand;
import com.example.contribtracker.command.NearCommand;
import com.example.contribtracker.websocket.WebSocketHandler;
import com.example.contribtracker.config.WebSocketConfig;
import com.example.contribtracker.util.LogHelper;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.io.File;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.ServerCommandSource;

public class ContribTrackerMod implements ModInitializer {
    public static final String MOD_ID = "contribtracker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static MinecraftServer server;
    private static final Map<UUID, Contribution> pendingContributions = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> contributionExpiryTimes = new ConcurrentHashMap<>();
    private static final long INVITATION_EXPIRY_TIME = 5 * 60 * 1000;
    
    // 高效线程池配置
    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 100;
    
    // 线程池
    public static final ExecutorService WORKER_POOL = Executors.newFixedThreadPool(MAX_POOL_SIZE, r -> {
        Thread thread = new Thread(r);
        thread.setName("ContribTracker-Worker");
        thread.setDaemon(true);
        return thread;
    });
    
    // 定时任务线程池
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread thread = new Thread(r);
        thread.setName("ContribTracker-Scheduler");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void onInitialize() {
        // 初始化配置目录
        setupConfigDirectories();
            
            // 注册命令
            registerCommands();
            
        // 异步初始化数据库和WebSocket
        WORKER_POOL.execute(() -> {
                try {
                LogHelper.info("异步初始化数据库...");
                DatabaseManager.initialize();
                LogHelper.info("数据库初始化成功");
            } catch (Exception e) {
                LogHelper.error("数据库初始化失败", e);
                }
            });
            
        WORKER_POOL.execute(() -> {
            try {
                LogHelper.info("异步初始化WebSocket...");
                WebSocketHandler.initialize();
                LogHelper.info("WebSocket初始化成功");
            } catch (Exception e) {
                LogHelper.error("WebSocket初始化失败", e);
            }
        });
        
        // 注册生命周期事件
        registerLifecycleEvents();
        
        // 启动定时任务
        startScheduledTasks();
        
        LogHelper.info("模组初始化完成");
    }
    
    private void setupConfigDirectories() {
        try {
            File configDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "null_city/contributions");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            LogHelper.info("配置目录创建成功: {}", configDir.getAbsolutePath());
        } catch (Exception e) {
            LogHelper.error("创建配置目录失败", e);
        }
    }

    private void registerLifecycleEvents() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
    }

    private void onServerStarting(MinecraftServer server) {
        ContribTrackerMod.server = server;
    }

    private void onServerStopping(MinecraftServer server) {
        try {
            LogHelper.info("正在关闭资源...");
            // 关闭WebSocket服务
            WebSocketHandler.shutdown();
            
            // 关闭数据库连接
            DatabaseManager.close();
            
            // 关闭线程池
            shutdownThreadPools();
            
            LogHelper.info("资源已安全关闭");
        } catch (Exception e) {
            LogHelper.error("关闭资源时发生错误", e);
        }
    }
    
    private void shutdownThreadPools() {
        try {
            // 优雅关闭线程池
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            WORKER_POOL.shutdown();
            if (!WORKER_POOL.awaitTermination(5, TimeUnit.SECONDS)) {
                WORKER_POOL.shutdownNow();
            }
            
            LogHelper.info("线程池已成功关闭");
        } catch (InterruptedException e) {
            LogHelper.error("关闭线程池时被中断", e);
            Thread.currentThread().interrupt();
        }
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

    private void startScheduledTasks() {
        // 邀请过期检查
        scheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            contributionExpiryTimes.entrySet().removeIf(entry -> {
                if (currentTime - entry.getValue() > INVITATION_EXPIRY_TIME) {
                    pendingContributions.remove(entry.getKey());
                    return true;
                }
                return false;
            });
        }, 60, 60, TimeUnit.SECONDS);
        
        LogHelper.info("定时任务已启动");
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(new AddCommand().register());
            dispatcher.register(new DeleteCommand().register());
            dispatcher.register(new ListCommand().register());
            dispatcher.register(new AcceptCommand().register());
            dispatcher.register(new RejectCommand().register());
            dispatcher.register(new RemoveCommand().register());
            dispatcher.register(new NearCommand().register());
        });
        
        LogHelper.info("命令注册完成");
    }
} 