package com.example.contribtracker.websocket;

import com.example.contribtracker.ContribTrackerMod;
import com.example.contribtracker.config.WebSocketConfig;
import com.example.contribtracker.database.DatabaseManager;
import com.example.contribtracker.database.Contribution;
import com.example.contribtracker.util.LogHelper;
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
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

public class WebSocketHandler {
    private static final Gson gson = new Gson();
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread thread = new Thread(r);
        thread.setName("ContribTracker-WebSocket-Heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<String, Long> lastPongTimes = new ConcurrentHashMap<>();
    private static final long PING_INTERVAL = 30; // 30秒
    private static final long PONG_TIMEOUT = 60; // 60秒
    // 缓存最后发送的数据，减少数据库访问
    private static volatile List<Contribution> cachedContributions = Collections.emptyList();
    private static volatile long cacheTimestamp = 0;
    private static final long CACHE_EXPIRY = 5000; // 5秒缓存
    private static final long DB_CHECK_INTERVAL = 1000; // 每秒检查数据库是否初始化

    public static void initialize() {
        if (isRunning.get()) {
            return;
        }

        try {
            String url = WebSocketConfig.getWsUrl();
            // 从URL中提取端口号
            int port = Integer.parseInt(url.split(":")[2].split("/")[0]);
            
            // 添加端口可用性检测和自动选择
            port = findAvailablePort(port);
            
            // 启动WebSocket服务器
            startServer(port);
            
            // 启动心跳检查
            startHeartbeat();
            
            // 定期尝试预热缓存，直到成功
            scheduleInitialCacheRefresh();
            
            isRunning.set(true);
            LogHelper.websocketUrl("ContribTrackerMod连接地址为：{}", url);
        } catch (Exception e) {
            LogHelper.error("WebSocket服务器启动失败", e);
        }
    }
    
    private static void scheduleInitialCacheRefresh() {
        ScheduledExecutorService initScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("ContribTracker-Cache-Initializer");
            thread.setDaemon(true);
            return thread;
        });
        
        final AtomicBoolean initialized = new AtomicBoolean(false);
        
        initScheduler.scheduleAtFixedRate(() -> {
            if (initialized.get()) {
                initScheduler.shutdown();
                return;
            }
            
            try {
                refreshCache();
                initialized.set(true);
                initScheduler.shutdown();
            } catch (Exception e) {
                LogHelper.debug("等待数据库初始化以刷新缓存...");
            }
        }, 0, DB_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private static void refreshCache() {
        CompletableFuture.runAsync(() -> {
            try {
                if (!DatabaseManager.isInitialized()) {
                    LogHelper.debug("数据库尚未初始化，跳过缓存刷新");
                    return;
                }
                
                cachedContributions = DatabaseManager.getAllContributions();
                cacheTimestamp = System.currentTimeMillis();
            } catch (SQLException e) {
                LogHelper.error("刷新数据缓存失败", e);
            }
        }, ContribTrackerMod.WORKER_POOL);
    }

    private static void startServer(int port) {
        try {
            // 创建WebSocket服务器
            ContribWebSocketServer server = new ContribWebSocketServer(new InetSocketAddress(port)) {
                @Override
                public void onOpen(WebSocketSession session) {
                    sessions.add(session);
                    LogHelper.debug("新的WebSocket连接: {}", session.getRemoteAddress());
                    // 异步发送所有数据
                    ContribTrackerMod.WORKER_POOL.execute(() -> sendAllData(session));
                }

                @Override
                public void onClose(WebSocketSession session) {
                    sessions.remove(session);
                    lastPongTimes.remove(session.getId());
                    LogHelper.debug("WebSocket连接关闭: {}", session.getRemoteAddress());
                }

                @Override
                public void onMessage(WebSocketSession session, String message) {
                    // 异步处理消息
                    ContribTrackerMod.WORKER_POOL.execute(() -> {
                    try {
                        handleMessage(session, message);
                    } catch (Exception e) {
                            LogHelper.error("处理WebSocket消息失败", e);
                        sendError(session, "消息处理失败: " + e.getMessage());
                    }
                    });
                }

                @Override
                public void onError(WebSocketSession session, Throwable error) {
                    LogHelper.error("WebSocket错误: {}", error.getMessage());
                    sessions.remove(session);
                    lastPongTimes.remove(session.getId());
                }
            };
            
            server.start();
        } catch (Exception e) {
            LogHelper.error("启动WebSocket服务器失败", e);
        }
    }

    private static void sendAllData(WebSocketSession session) {
        try {
            // 检查缓存是否有效，无效则刷新
            List<Contribution> contributions = getCachedContributions();
            
            JsonObject message = new JsonObject();
            message.addProperty("type", "all_data");
            message.add("data", gson.toJsonTree(contributions));
            session.send(gson.toJson(message));
            LogHelper.debug("已发送所有贡献数据到客户端: {}", session.getRemoteAddress());
        } catch (Exception e) {
            LogHelper.error("发送所有贡献数据失败", e);
            sendError(session, "获取贡献数据失败");
        }
    }
    
    private static List<Contribution> getCachedContributions() throws SQLException {
        long now = System.currentTimeMillis();
        if (now - cacheTimestamp > CACHE_EXPIRY) {
            synchronized (WebSocketHandler.class) {
                if (now - cacheTimestamp > CACHE_EXPIRY) {
                    cachedContributions = DatabaseManager.getAllContributions();
                    cacheTimestamp = now;
                }
            }
        }
        return cachedContributions;
    }

    public static void broadcastContributionUpdate(Contribution contribution) {
        // 异步广播更新
        CompletableFuture.runAsync(() -> {
        try {
                // 更新缓存
                refreshCache();
                
            JsonObject message = new JsonObject();
            message.addProperty("type", "update_data");
            message.add("data", gson.toJsonTree(contribution));
            broadcastUpdate(message);
                LogHelper.debug("已广播贡献更新: {}", contribution.getId());
        } catch (Exception e) {
                LogHelper.error("广播贡献更新失败", e);
        }
        }, ContribTrackerMod.WORKER_POOL);
    }

    private static void sendError(WebSocketSession session, String error) {
        try {
        JsonObject message = new JsonObject();
        message.addProperty("type", "error");
        message.addProperty("data", error);
        session.send(gson.toJson(message));
        } catch (Exception e) {
            LogHelper.error("发送错误消息失败", e);
        }
    }

    private static void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            
            // 发送ping消息，并处理超时会话
            CompletableFuture.runAsync(() -> {
                // 分批次处理，避免大量会话时阻塞
                int batchSize = 10;
                int count = 0;
                
            for (WebSocketSession session : sessions) {
                try {
                        // 批次处理，每10个会话暂停一下
                        if (++count % batchSize == 0) {
                            Thread.sleep(50);
                        }
                        
                        // 发送ping消息
                    JsonObject pingMessage = new JsonObject();
                    pingMessage.addProperty("type", "ping");
                    session.send(gson.toJson(pingMessage));
                } catch (Exception e) {
                        LogHelper.error("发送ping消息失败", e);
                    sessions.remove(session);
                }
            }
            
            // 检查超时
            lastPongTimes.entrySet().removeIf(entry -> {
                if (currentTime - entry.getValue() > PONG_TIMEOUT * 1000) {
                        LogHelper.warn("客户端超时: {}", entry.getKey());
                    return true;
                }
                return false;
            });
            }, ContribTrackerMod.WORKER_POOL);
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
                    LogHelper.warn("收到未知类型的消息: {}", type);
            }
        } catch (Exception e) {
            LogHelper.error("处理WebSocket消息失败", e);
            sendError(session, "消息处理失败: " + e.getMessage());
        }
    }

    private static void handleCheckData(WebSocketSession session) {
        CompletableFuture.runAsync(() -> {
        try {
                List<Contribution> contributions = getCachedContributions();
            JsonObject response = new JsonObject();
            response.addProperty("type", "all_data");
            response.add("data", gson.toJsonTree(contributions));
            session.send(gson.toJson(response));
            } catch (Exception e) {
                LogHelper.error("获取贡献数据失败", e);
            sendError(session, "获取数据失败: " + e.getMessage());
        }
        }, ContribTrackerMod.WORKER_POOL);
    }

    public static void broadcastUpdate(JsonObject data) {
        // 分批次广播，避免一次性处理过多会话
        int batchSize = 10;
        int count = 0;
        
        for (WebSocketSession session : sessions) {
            CompletableFuture.runAsync(() -> {
            try {
                session.send(gson.toJson(data));
            } catch (Exception e) {
                    LogHelper.error("广播消息失败", e);
                }
            }, ContribTrackerMod.WORKER_POOL);
            
            // 控制批次
            if (++count % batchSize == 0) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public static void shutdown() {
        if (!isRunning.get()) {
            return;
        }

        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            for (WebSocketSession session : sessions) {
                try {
                session.close();
                } catch (Exception e) {
                    LogHelper.error("关闭会话出错", e);
                }
            }
            
            sessions.clear();
            lastPongTimes.clear();
            cachedContributions.clear();
            isRunning.set(false);
        } catch (Exception e) {
            LogHelper.error("关闭WebSocket服务器时出错", e);
        }
    }

    /**
     * 查找可用的端口
     * @param startPort 起始端口号
     * @return 可用的端口号
     */
    private static int findAvailablePort(int startPort) {
        int port = startPort;
        int maxPort = startPort + 100; // 最多尝试100个端口
        
        while (port < maxPort) {
            try {
                java.net.ServerSocket serverSocket = new java.net.ServerSocket(port);
                serverSocket.close();
                return port;
            } catch (java.io.IOException e) {
                port++;
            }
        }
        
        LogHelper.warn("无法找到可用端口，尝试使用默认端口 {}", startPort);
        return startPort;
    }
} 