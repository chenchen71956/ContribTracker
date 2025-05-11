package com.example.contribtracker.websocket;

import com.example.contribtracker.ContribTrackerMod;
import com.example.contribtracker.config.WebSocketConfig;
import com.example.contribtracker.database.DatabaseManager;
import com.example.contribtracker.database.Contribution;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.sql.SQLException;

public class WebSocketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);
    private static final Gson gson = new Gson();
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final Map<String, Long> lastPongTimes = new ConcurrentHashMap<>();
    private static final long PING_INTERVAL = 30; // 30秒
    private static final long PONG_TIMEOUT = 60; // 60秒

    public static void initialize() {
        if (isRunning.get()) {
            return;
        }

        try {
            String url = WebSocketConfig.getWsUrl();
            // 从URL中提取端口号
            int port = Integer.parseInt(url.split(":")[2].split("/")[0]);
            
            // 启动WebSocket服务器
            startServer(port);
            
            // 启动心跳检查
            startHeartbeat();
            
            isRunning.set(true);
            LOGGER.info("WebSocket服务器启动成功，监听端口: {}", port);
        } catch (Exception e) {
            LOGGER.error("WebSocket服务器启动失败", e);
        }
    }

    private static void startServer(int port) {
        try {
            // 创建WebSocket服务器
            ContribWebSocketServer server = new ContribWebSocketServer(new InetSocketAddress(port)) {
                @Override
                public void onOpen(WebSocketSession session) {
                    sessions.add(session);
                    LOGGER.info("新的WebSocket连接: {}", session.getRemoteAddress());
                    // 发送所有数据
                    sendAllData(session);
                }

                @Override
                public void onClose(WebSocketSession session) {
                    sessions.remove(session);
                    lastPongTimes.remove(session.getId());
                    LOGGER.info("WebSocket连接关闭: {}", session.getRemoteAddress());
                }

                @Override
                public void onMessage(WebSocketSession session, String message) {
                    try {
                        handleMessage(session, message);
                    } catch (Exception e) {
                        LOGGER.error("处理WebSocket消息失败", e);
                        sendError(session, "消息处理失败: " + e.getMessage());
                    }
                }

                @Override
                public void onError(WebSocketSession session, Throwable error) {
                    LOGGER.error("WebSocket错误: {}", error.getMessage());
                    sessions.remove(session);
                    lastPongTimes.remove(session.getId());
                }
            };
            
            server.start();
        } catch (Exception e) {
            LOGGER.error("启动WebSocket服务器失败", e);
        }
    }

    private static void sendAllData(WebSocketSession session) {
        try {
            List<Contribution> contributions = DatabaseManager.getAllContributions();
            JsonObject message = new JsonObject();
            message.addProperty("type", "all_data");
            message.add("data", gson.toJsonTree(contributions));
            session.send(gson.toJson(message));
            LOGGER.info("已发送所有数据到客户端: {}", session.getRemoteAddress());
        } catch (SQLException e) {
            LOGGER.error("发送所有数据失败", e);
            sendError(session, "获取数据失败");
        }
    }

    public static void broadcastContributionUpdate(Contribution contribution) {
        try {
            JsonObject message = new JsonObject();
            message.addProperty("type", "update_data");
            message.add("data", gson.toJsonTree(contribution));
            broadcastUpdate(message);
            LOGGER.info("已广播贡献更新: {}", contribution.getId());
        } catch (Exception e) {
            LOGGER.error("广播贡献更新失败", e);
        }
    }

    private static void sendError(WebSocketSession session, String error) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "error");
        message.addProperty("data", error);
        session.send(gson.toJson(message));
    }

    private static void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            
            // 发送ping消息
            for (WebSocketSession session : sessions) {
                try {
                    JsonObject pingMessage = new JsonObject();
                    pingMessage.addProperty("type", "ping");
                    session.send(gson.toJson(pingMessage));
                } catch (Exception e) {
                    LOGGER.error("发送ping消息失败", e);
                    sessions.remove(session);
                }
            }
            
            // 检查超时
            lastPongTimes.entrySet().removeIf(entry -> {
                if (currentTime - entry.getValue() > PONG_TIMEOUT * 1000) {
                    LOGGER.warn("客户端超时: {}", entry.getKey());
                    return true;
                }
                return false;
            });
        }, PING_INTERVAL, PING_INTERVAL, TimeUnit.SECONDS);
    }

    private static void handlePong(WebSocketSession session) {
        lastPongTimes.put(session.getId(), System.currentTimeMillis());
    }

    private static void handleMessage(WebSocketSession session, String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String type = json.get("type").getAsString();
            
            switch (type) {
                case "pong":
                    handlePong(session);
                    break;
                case "check_data":
                    handleCheckData(session);
                    break;
                default:
                    LOGGER.warn("收到未知类型的消息: {}", type);
            }
        } catch (Exception e) {
            LOGGER.error("处理WebSocket消息失败", e);
            sendError(session, "消息处理失败: " + e.getMessage());
        }
    }

    private static void handleCheckData(WebSocketSession session) {
        try {
            List<Contribution> contributions = DatabaseManager.getAllContributions();
            JsonObject response = new JsonObject();
            response.addProperty("type", "all_data");
            response.add("data", gson.toJsonTree(contributions));
            session.send(gson.toJson(response));
        } catch (SQLException e) {
            LOGGER.error("获取贡献数据失败", e);
            sendError(session, "获取数据失败: " + e.getMessage());
        }
    }

    public static void broadcastUpdate(JsonObject data) {
        for (WebSocketSession session : sessions) {
            try {
                session.send(gson.toJson(data));
            } catch (Exception e) {
                LOGGER.error("广播消息失败", e);
            }
        }
    }

    public static void shutdown() {
        if (!isRunning.get()) {
            return;
        }

        try {
            scheduler.shutdown();
            for (WebSocketSession session : sessions) {
                session.close();
            }
            sessions.clear();
            lastPongTimes.clear();
            isRunning.set(false);
            LOGGER.info("WebSocket服务器已关闭");
        } catch (Exception e) {
            LOGGER.error("关闭WebSocket服务器时出错", e);
        }
    }
} 