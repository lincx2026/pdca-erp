package com.pcba.erp.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * JWT 鉴权过滤器：请求头带 Authorization: Bearer <token>
 * 1. 解析 token → 取 userId → 从 Redis 取 LoginUser 缓存 (key = login_tokens:{uid})
 * 2. 有则放入 SecurityContext；没有则放行（后续匿名接口 OK，需要登录的会被 401）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";
    public static final String LOGIN_TOKEN_PREFIX = "login_tokens:";

    private final JwtTokenUtil jwtTokenUtil;
    private final StringRedisTemplate redisTemplate;

    @Value("${pcba.jwt.expire-minutes:720}")
    private long expireMinutes;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {

        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith(PREFIX)) {
            chain.doFilter(req, resp);
            return;
        }
        String token = auth.substring(PREFIX.length());
        Claims claims = jwtTokenUtil.parse(token);
        if (claims == null || jwtTokenUtil.isExpired(claims)) {
            chain.doFilter(req, resp);
            return;
        }
        Long uid = null;
        Object uidObj = claims.get(JwtTokenUtil.CLAIM_USER_ID);
        if (uidObj instanceof Number) uid = ((Number) uidObj).longValue();
        if (uid == null) {
            chain.doFilter(req, resp);
            return;
        }

        // 从 Redis 取 LoginUser JSON
        String cacheKey = LOGIN_TOKEN_PREFIX + uid;
        String json = redisTemplate.opsForValue().get(cacheKey);
        if (json != null) {
            try {
                LoginUser loginUser = com.pcba.erp.common.core.utils.JsonUtils.fromJson(json, LoginUser.class);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                // 续期: 剩余 < 20% 自动续期
                Long ttl = redisTemplate.getExpire(cacheKey, TimeUnit.MINUTES);
                if (ttl != null && ttl < expireMinutes * 0.2) {
                    redisTemplate.expire(cacheKey, expireMinutes, TimeUnit.MINUTES);
                }
            } catch (Exception e) {
                log.warn("反序列化 LoginUser 失败, uid={}", uid, e);
            }
        }
        chain.doFilter(req, resp);
    }
}
