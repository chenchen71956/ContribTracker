package com.example.contribtracker.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.contribtracker.ContribTrackerMod;

/**
 * 日志工具类，统一输出格式
 */
public class LogHelper {
    private static final String PREFIX = "ContribTracker";
    private static final Logger LOGGER = LoggerFactory.getLogger(PREFIX);
    
    /**
     * 输出INFO级别日志
     */
    public static void info(String message) {
        LOGGER.info(message);
    }
    
    /**
     * 输出INFO级别日志，带参数
     */
    public static void info(String format, Object... args) {
        LOGGER.info(format, args);
    }
    
    /**
     * 输出DEBUG级别日志
     */
    public static void debug(String message) {
        LOGGER.debug(message);
    }
    
    /**
     * 输出DEBUG级别日志，带参数
     */
    public static void debug(String format, Object... args) {
        LOGGER.debug(format, args);
    }
    
    /**
     * 输出WARN级别日志
     */
    public static void warn(String message) {
        LOGGER.warn(message);
    }
    
    /**
     * 输出WARN级别日志，带参数
     */
    public static void warn(String format, Object... args) {
        LOGGER.warn(format, args);
    }
    
    /**
     * 输出ERROR级别日志
     */
    public static void error(String message) {
        LOGGER.error(message);
    }
    
    /**
     * 输出ERROR级别日志，带参数
     */
    public static void error(String message, Object... args) {
        LOGGER.error(message, args);
    }
    
    /**
     * 输出ERROR级别日志，带异常
     */
    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }
} 