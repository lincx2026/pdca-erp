package com.pcba.erp.common.security;

import com.pcba.erp.common.core.utils.SecurityUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 登出成功处理器: 清 Redis 登录态, 返回成功 JSON */
@Component
@RequiredArgsConstructor
public class LogoutSuccessHandlerImpl implements LogoutSuccessHandler {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void onLogoutSuccess(HttpServletRequest req, HttpServletResponse resp, Authentication auth)
            throws IOException, ServletException {
        if (auth != null && auth.getPrincipal() instanceof LoginUser) {
            Long uid = ((LoginUser) auth.getPrincipal()).getUserId();
            redisTemplate.delete(JwtAuthenticationTokenFilter.LOGIN_TOKEN_PREFIX + uid);
        }
        SecurityUtils.writeJson(resp, HttpStatus.OK.value(), "退出成功");
    }
}
