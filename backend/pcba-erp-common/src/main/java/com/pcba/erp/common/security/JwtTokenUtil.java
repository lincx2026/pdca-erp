package com.pcba.erp.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具: 生成/校验 Token。密钥从 application.yml 读取
 */
@Slf4j
@Component
public class JwtTokenUtil {

    /** 存 Claims 里的用户ID key */
    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_USERNAME = "uname";

    @Value("${pcba.jwt.secret}")
    private String secret;

    @Value("${pcba.jwt.expire-minutes:720}")
    private long expireMinutes;

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || secret.startsWith("replace-") || secret.contains("REPLACE-ME")) {
            throw new IllegalStateException("JWT_SECRET 必须配置至少 32 字节的随机密钥");
        }
    }

    private SecretKey getKey() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>(4);
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_USERNAME, username);
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expireMinutes * 60 * 1000L))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** 解析 token 失败返回 null */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("JWT parse failed: {}", e.getMessage());
            return null;
        }
    }

    public boolean isExpired(Claims claims) {
        return claims == null || claims.getExpiration().before(new Date());
    }

    public long getExpireMinutes() {
        return expireMinutes;
    }
}
