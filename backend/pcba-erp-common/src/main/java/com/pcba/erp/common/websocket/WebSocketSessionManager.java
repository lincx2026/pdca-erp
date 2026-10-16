package com.pcba.erp.common.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理器 — 维护 userId → session 映射，支持定向推送。
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String userId, WebSocketSession session) {
        // 用并发安全装饰器包装，限制发送缓冲 1MB，超时 5s
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(session, 5000, 1024 * 1024);
        WebSocketSession old = sessions.put(userId, decorated);
        if (old != null) {
            try { old.close(); } catch (Exception e) { /* ignore */ }
        }
        log.info("[WebSocket] 用户 {} 已连接，当前在线: {}", userId, sessions.size());
    }

    public void remove(String userId) {
        sessions.remove(userId);
        log.info("[WebSocket] 用户 {} 已断开，当前在线: {}", userId, sessions.size());
    }

    /** 发送消息给指定用户 */
    public void sendToUser(String userId, String message) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                log.error("[WebSocket] 推送给用户 {} 失败: {}", userId, e.getMessage());
            }
        }
    }

    /** 广播给所有在线用户 */
    public void sendToAll(String message) {
        sessions.forEach((userId, session) -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (Exception e) {
                    log.error("[WebSocket] 广播给用户 {} 失败: {}", userId, e.getMessage());
                }
            }
        });
    }

    public int getOnlineCount() {
        return sessions.size();
    }
}
