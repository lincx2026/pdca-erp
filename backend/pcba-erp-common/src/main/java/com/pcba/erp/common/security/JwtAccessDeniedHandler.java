package com.pcba.erp.common.security;

import com.pcba.erp.common.core.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 已登录但权限不足 403 */
@Slf4j
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest req, HttpServletResponse resp, AccessDeniedException e)
            throws IOException {
        log.warn("403 权限不足: {} {}", req.getMethod(), req.getRequestURI());
        SecurityUtils.writeJson(resp, HttpStatus.FORBIDDEN.value(), "权限不足, 无法访问该资源");
    }
}
