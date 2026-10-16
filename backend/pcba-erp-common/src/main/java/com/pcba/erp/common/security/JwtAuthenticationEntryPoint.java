package com.pcba.erp.common.security;

import com.pcba.erp.common.core.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 未认证访问受保护资源时返回 JSON 401 而不是跳登录页 */
@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest req, HttpServletResponse resp, AuthenticationException e)
            throws IOException {
        log.warn("401 未登录访问: {} {}", req.getMethod(), req.getRequestURI());
        SecurityUtils.writeJson(resp, HttpStatus.UNAUTHORIZED.value(), "未登录或登录已过期,请重新登录");
    }
}
