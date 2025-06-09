package com.example.contribtracker.websocket;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.contribtracker.ContribTrackerMod;
import com.example.contribtracker.util.LogHelper;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public abstract class ContribWebSocketServer extends WebSocketServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContribTrackerMod.MOD_ID);
    private final Map<WebSocket, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public ContribWebSocketServer(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        WebSocketSession session = new WebSocketSession(conn);
        sessions.put(conn, session);
        onOpen(session);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        WebSocketSession session = sessions.remove(conn);
        if (session != null) {
            onClose(session);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        WebSocketSession session = sessions.get(conn);
        if (session != null) {
            onMessage(session, message);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        WebSocketSession session = sessions.get(conn);
        if (session != null) {
            onError(session, ex);
        }
    }

    @Override
    public void onStart() {
    }

    public abstract void onOpen(WebSocketSession session);
    public abstract void onClose(WebSocketSession session);
    public abstract void onMessage(WebSocketSession session, String message);
    public abstract void onError(WebSocketSession session, Throwable error);
} 