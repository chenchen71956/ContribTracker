package com.example.contribtracker.config;

import com.example.contribtracker.ContribTrackerMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class WebSocketConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);
    private static final String CONFIG_FILE = "websocket.yml";
    private static String wsUrl = "ws://127.0.0.1:25580/ws";

    public static void initialize(File configDir) {
        File configFile = new File(configDir, CONFIG_FILE);
        
        if (!configFile.exists()) {
            createDefaultConfig(configFile);
        }
        
        loadConfig(configFile);
    }

    private static void createDefaultConfig(File configFile) {
        try {
            Map<String, Object> config = new HashMap<>();
            Map<String, String> websocket = new HashMap<>();
            websocket.put("url", wsUrl);
            config.put("websocket", websocket);

            Yaml yaml = new Yaml();
            try (FileWriter writer = new FileWriter(configFile)) {
                yaml.dump(config, writer);
            }
            
            LOGGER.info("已创建默认WebSocket配置文件");
        } catch (IOException e) {
            LOGGER.error("创建WebSocket配置文件失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadConfig(File configFile) {
        try {
            Yaml yaml = new Yaml();
            try (FileInputStream input = new FileInputStream(configFile)) {
                Map<String, Object> config = yaml.load(input);
                if (config != null && config.containsKey("websocket")) {
                    Object websocketObj = config.get("websocket");
                    if (websocketObj instanceof Map) {
                        Map<String, Object> websocket = (Map<String, Object>) websocketObj;
                        Object urlObj = websocket.get("url");
                        if (urlObj instanceof String) {
                            String url = (String) urlObj;
                            if (validateUrl(url)) {
                                wsUrl = url;
                                LOGGER.info("已加载WebSocket配置: {}", wsUrl);
                            } else {
                                LOGGER.error("WebSocket URL格式无效，必须使用127.0.0.1作为IP地址");
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("加载WebSocket配置文件失败", e);
        }
    }

    private static boolean validateUrl(String url) {
        // 检查URL格式
        if (!url.startsWith("ws://127.0.0.1:")) {
            return false;
        }
        
        // 检查端口号
        String[] parts = url.split(":");
        if (parts.length != 3) {
            return false;
        }
        
        try {
            int port = Integer.parseInt(parts[2].split("/")[0]);
            return port > 0 && port < 65536;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String getWsUrl() {
        return wsUrl;
    }
} 