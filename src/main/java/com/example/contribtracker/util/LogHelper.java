package com.example.contribtracker.util;

import com.example.contribtracker.ContribTrackerMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * 日志辅助工具类
 * 统一日志输出格式并提供便捷方法
 */
public class LogHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);
    private static final String LOG_PREFIX = "[ContribTracker/{}] ";
    
    // 定义特殊标记
    private static final Marker STARTUP_MARKER = MarkerFactory.getMarker("STARTUP");
    private static final Marker WEBSOCKET_URL_MARKER = MarkerFactory.getMarker("WEBSOCKET_URL");

    /**
     * 输出DEBUG级别日志
     * @param message 日志消息
     * @param args 参数
     */
    public static void debug(String message, Object... args) {
        LOGGER.debug(LOG_PREFIX + message, "DEBUG", args);
    }

    /**
     * 输出INFO级别日志
     * @param message 日志消息
     * @param args 参数
     */
    public static void info(String message, Object... args) {
        LOGGER.info(LOG_PREFIX + message, "INFO", args);
    }

    /**
     * 输出WARN级别日志
     * @param message 日志消息
     * @param args 参数
     */
    public static void warn(String message, Object... args) {
        LOGGER.warn(LOG_PREFIX + message, "WARN", args);
    }

    /**
     * 输出ERROR级别日志
     * @param message 日志消息
     * @param args 参数
     */
    public static void error(String message, Object... args) {
        LOGGER.error(LOG_PREFIX + message, "ERROR", args);
    }

    /**
     * 输出带异常的ERROR级别日志
     * @param message 日志消息
     * @param throwable 异常
     */
    public static void error(String message, Throwable throwable) {
        LOGGER.error(LOG_PREFIX.replace("{}", "ERROR") + message, throwable);
    }
    
    /**
     * 输出应用启动信息（始终显示）
     * @param message 日志消息
     * @param args 参数
     */
    public static void startup(String message, Object... args) {
        LOGGER.info(STARTUP_MARKER, LOG_PREFIX + message, "STARTUP", args);
    }
    
    /**
     * 输出WebSocket地址信息（始终显示）
     * @param message 日志消息
     * @param args 参数
     */
    public static void websocketUrl(String message, Object... args) {
        LOGGER.info(WEBSOCKET_URL_MARKER, LOG_PREFIX + message, "WEBSOCKET", args);
    }
} 