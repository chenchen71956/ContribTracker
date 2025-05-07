package cn.kongchengli.cn.contribtracker.websocket;

import cn.kongchengli.cn.contribtracker.database.DatabaseManager;
import cn.kongchengli.cn.contribtracker.database.Contribution;
import cn.kongchengli.cn.contribtracker.database.ContributorInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.websocket.WsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("ContribTracker");
    private static final Gson gson = new GsonBuilder().create();
    private static final Map<String, WsContext> clients = new ConcurrentHashMap<>();
    
    /**
     * 处理WebSocket连接
     */
    public static void onConnect(WsContext ctx) {
        String clientId = ctx.getSessionId();
        clients.put(clientId, ctx);
        LOGGER.info("WebSocket客户端已连接: {}", clientId);
        
        // 发送初始数据
        sendAllData(ctx);
    }
    
    /**
     * 处理WebSocket断开连接
     */
    public static void onClose(WsContext ctx) {
        String clientId = ctx.getSessionId();
        clients.remove(clientId);
        LOGGER.info("WebSocket客户端已断开: {}", clientId);
    }
    
    /**
     * 处理WebSocket消息
     */
    public static void onMessage(WsContext ctx, String message) {
        try {
            WebSocketMessage wsMessage = gson.fromJson(message, WebSocketMessage.class);
            
            switch (wsMessage.getType()) {
                case "check":
                    // 收到检查请求，发送所有数据
                    sendAllData(ctx);
                    break;
                default:
                    LOGGER.warn("收到未知类型的消息: {}", wsMessage.getType());
            }
        } catch (Exception e) {
            LOGGER.error("处理WebSocket消息失败", e);
        }
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
            for (WsContext client : clients.values()) {
                client.send(message);
            }
            
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
} 