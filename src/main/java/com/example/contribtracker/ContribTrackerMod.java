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
import com.example.contribtracker.command.CommandManager;

public class ContribTrackerMod implements ModInitializer {
    public static final String MOD_ID = "contribtracker";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static MinecraftServer server;
    private static final Map<UUID, Contribution> pendingContributions = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> contributionExpiryTimes = new ConcurrentHashMap<>();
    private static final long INVITATION_EXPIRY_TIME = 5 * 60 * 1000;

    @Override
    public void onInitialize() {
        try {
            DatabaseManager.initialize();
            LOGGER.info("数据库初始化成功");
        } catch (Exception e) {
            LOGGER.error("数据库初始化失败", e);
            return;
        }
        CommandManager.registerCommands();
        LOGGER.info("ContribTracker 模组初始化完成");
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
} 