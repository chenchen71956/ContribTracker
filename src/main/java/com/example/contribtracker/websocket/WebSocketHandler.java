package com.example.contribtracker.websocket;

import com.example.contribtracker.database.DatabaseManager;
import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.database.ContributorInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WebSocketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketHandler.class);
    private static final Gson gson = new GsonBuilder().create();
    private static final Map<WsContext, Long> clients = new ConcurrentHashMap<>();
    private static final Map<WsContext, Long> lastPingTime = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final long PING_INTERVAL = 30000; // 30秒发送一次ping
    private static final long PONG_TIMEOUT = 60000;  // 60秒没有收到pong就断开连接
    
    static {
        // 启动保活检查任务
        scheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            clients.forEach((ctx, lastPing) -> {
                if (currentTime - lastPing > PONG_TIMEOUT) { // 60秒没有响应就断开连接
                    try {
                        ctx.session.close();
                        clients.remove(ctx);
                        lastPingTime.remove(ctx);
                    } catch (Exception e) {
                        LOGGER.error("关闭WebSocket连接时发生错误", e);
                    }
                } else {
                    sendPing(ctx);
                }
            });
        }, PING_INTERVAL, PING_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 处理WebSocket连接
     */
    public static void onConnect(WsContext ctx) {
        LOGGER.info("新的WebSocket连接: {}", ctx.session.getRemoteAddress());
        clients.put(ctx, System.currentTimeMillis());
        lastPingTime.put(ctx, System.currentTimeMillis());
        sendAllData(ctx);
    }
    
    /**
     * 处理WebSocket断开连接
     */
    public static void onClose(WsContext ctx) {
        LOGGER.info("WebSocket连接关闭: {}", ctx.session.getRemoteAddress());
        clients.remove(ctx);
        lastPingTime.remove(ctx);
    }
    
    /**
     * 处理WebSocket消息
     */
    public static void onMessage(WsMessageContext ctx) {
        try {
            String message = ctx.message();
            WebSocketMessage wsMessage = gson.fromJson(message, WebSocketMessage.class);
            
            if ("check".equals(wsMessage.getType())) {
                // 发送所有数据
                List<Contribution> contributions = DatabaseManager.getAllContributions();
                WebSocketMessage response = new WebSocketMessage("all_data", contributions);
                ctx.send(gson.toJson(response));
            } else if ("pong".equals(wsMessage.getType())) {
                // 更新最后ping时间
                lastPingTime.put(ctx, System.currentTimeMillis());
            }
        } catch (Exception e) {
            LOGGER.error("处理WebSocket消息时出错", e);
            WebSocketMessage errorMessage = new WebSocketMessage("error", "处理消息时出错");
            ctx.send(gson.toJson(errorMessage));
        }
    }
    
    /**
     * 发送ping消息
     */
    private static void sendPing(WsContext ctx) {
        WebSocketMessage ping = new WebSocketMessage();
        ping.setType("ping");
        ctx.send(gson.toJson(ping));
    }
    
    /**
     * 发送所有数据到指定客户端
     */
    private static void sendAllData(WsContext ctx) {
        try {
            // 获取所有贡献
            List<Contribution> contributions = DatabaseManager.getAllContributions();
            
            // 获取所有贡献者信息
            for (Contribution contribution : contributions) {
                List<ContributorInfo> contributors = DatabaseManager.getContributorsByContributionId(contribution.getId());
                contribution.setContributorList(contributors);
            }
            
            // 创建响应消息
            WebSocketMessage response = new WebSocketMessage();
            response.setType("all_data");
            response.setData(contributions);
            
            // 发送数据
            ctx.send(gson.toJson(response));
            
        } catch (SQLException e) {
            LOGGER.error("获取数据失败", e);
            sendError(ctx, "获取数据失败");
        }
    }
    
    /**
     * 广播更新消息给所有客户端
     */
    public static void broadcastUpdate(Contribution contribution) {
        try {
            // 获取贡献者信息
            List<ContributorInfo> contributors = DatabaseManager.getContributorsByContributionId(contribution.getId());
            contribution.setContributorList(contributors);
            
            // 创建更新消息
            WebSocketMessage update = new WebSocketMessage();
            update.setType("update");
            update.setData(contribution);
            
            // 广播给所有客户端
            String message = gson.toJson(update);
            clients.keySet().forEach(ctx -> {
                try {
                    ctx.send(message);
                } catch (Exception e) {
                    LOGGER.error("广播更新时发生错误", e);
                }
            });
            
        } catch (SQLException e) {
            LOGGER.error("广播更新失败", e);
        }
    }
    
    /**
     * 发送错误消息
     */
    private static void sendError(WsContext ctx, String error) {
        WebSocketMessage response = new WebSocketMessage();
        response.setType("error");
        response.setData(error);
        ctx.send(gson.toJson(response));
    }
    
    /**
     * 关闭WebSocket处理器
     */
    public static void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
} 