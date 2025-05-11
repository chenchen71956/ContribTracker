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
import com.example.contribtracker.websocket.WebSocketHandler;
import com.example.contribtracker.config.WebSocketConfig;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
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
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Override
    public void onInitialize() {
        // 设置线程名称
        Thread.currentThread().setName("ContribTracker");
        
        try {
            // 初始化数据库
            DatabaseManager.initialize();
            LOGGER.info("数据库初始化成功");
            
            // 初始化WebSocket
            WebSocketHandler.initialize();
            LOGGER.info("WebSocket初始化成功");
            
            // 注册命令
            registerCommands();
            
            // 注册服务器停止事件
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
                try {
                    DatabaseManager.close();
                    LOGGER.info("数据库连接已关闭");
                } catch (SQLException e) {
                    LOGGER.error("关闭数据库连接失败", e);
                }
            });
            
        } catch (Exception e) {
            LOGGER.error("模组初始化失败", e);
        }
    }

    private void onServerStarting(MinecraftServer server) {
        ContribTrackerMod.server = server;
    }

    private void onServerStopping(MinecraftServer server) {
        try {
            DatabaseManager.close();
        } catch (SQLException e) {
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
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
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

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(new AddCommand().register());
            dispatcher.register(new DeleteCommand().register());
            dispatcher.register(new ListCommand().register());
            dispatcher.register(new AcceptCommand().register());
            dispatcher.register(new RejectCommand().register());
        });
    }
} 