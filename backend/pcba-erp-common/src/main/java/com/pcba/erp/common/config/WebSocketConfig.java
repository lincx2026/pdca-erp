package com.pcba.erp.common.config;

import com.pcba.erp.common.security.JwtTokenUtil;
import com.pcba.erp.common.websocket.NoticeWebSocketHandler;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.Map;

/**
 * WebSocket 配置 — 注册 /ws/notice 端点，握手时从 JWT 提取 userId。
 */
@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NoticeWebSocketHandler handler;
    private final JwtTokenUtil jwtTokenUtil;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/notice")
                .addInterceptors(jwtHandshakeInterceptor())
                .setAllowedOrigins("*");
    }

    /** 从 URL 查询参数 ?token=xxx 提取 JWT，解析出 userId */
    private HandshakeInterceptor jwtHandshakeInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler wsHandler, Map<String, Object> attributes) {
                String query = request.getURI().getQuery();
                if (query == null) return false;
                String token = null;
                for (String p : query.split("&")) {
                    if (p.startsWith("token=")) {
                        token = p.substring(6);
                        break;
                    }
                }
                if (token == null || token.isEmpty()) {
                    log.warn("[WebSocket] 握手失败: 缺少 token 参数");
                    return false;
                }
                Claims claims = jwtTokenUtil.parse(token);
                if (claims == null) {
                    log.warn("[WebSocket] 握手失败: JWT 无效");
                    return false;
                }
                Object uid = claims.get(JwtTokenUtil.CLAIM_USER_ID);
                if (uid == null) {
                    log.warn("[WebSocket] 握手失败: JWT 中无 uid");
                    return false;
                }
                attributes.put("userId", Long.valueOf(uid.toString()));
                Object uname = claims.get(JwtTokenUtil.CLAIM_USERNAME);
                if (uname != null) attributes.put("username", uname.toString());
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Exception ex) {
                // no-op
            }
        };
    }
}
