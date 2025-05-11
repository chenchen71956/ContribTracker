package com.example.contribtracker.websocket;

import org.java_websocket.WebSocket;
import java.net.InetSocketAddress;

public class WebSocketSession {
    private final WebSocket connection;
    private final String id;

    public WebSocketSession(WebSocket connection) {
        this.connection = connection;
        this.id = connection.getRemoteSocketAddress().toString();
    }

    public String getId() {
        return id;
    }

    public InetSocketAddress getRemoteAddress() {
        return connection.getRemoteSocketAddress();
    }

    public void send(String message) {
        connection.send(message);
    }

    public void close() {
        connection.close();
    }
} 