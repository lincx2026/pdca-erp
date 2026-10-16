package com.pcba.erp.common.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 通知 WebSocket 处理器 — 建立连接时注册，断开时移除，处理心跳。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.register(String.valueOf(userId), session);
        } else {
            log.warn("[WebSocket] 连接缺少 userId 属性，关闭");
            try { session.close(CloseStatus.POLICY_VIOLATION); } catch (Exception e) { /* ignore */ }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        // 心跳：客户端发 "ping"，服务端回 "pong"
        if ("ping".equalsIgnoreCase(payload)) {
            try {
                session.sendMessage(new TextMessage("pong"));
            } catch (Exception e) { /* ignore */ }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object userId = session.getAttributes().get("userId");
        if (userId != null) {
            sessionManager.remove(String.valueOf(userId));
        }
    }
}
