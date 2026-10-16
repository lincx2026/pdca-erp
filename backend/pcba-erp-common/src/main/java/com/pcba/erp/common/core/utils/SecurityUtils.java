package com.pcba.erp.common.core.utils;

import com.pcba.erp.common.core.R;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.common.security.LoginUser;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;

/**
 * 安全工具类：获取当前登录用户、密码加密、JSON写出
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    public static String encodePassword(String raw) {
        return ENCODER.encode(raw);
    }

    public static boolean matches(String raw, String encoded) {
        return ENCODER.matches(raw, encoded);
    }

    /** 获取当前 Authentication */
    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /** 获取登录用户主体, 未登录抛 401 */
    public static LoginUser getLoginUser() {
        Authentication auth = getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser)) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED.value(), "未登录或登录已过期");
        }
        return (LoginUser) auth.getPrincipal();
    }

    /** 允许匿名场景下安全取, 可能返回 null */
    public static LoginUser getLoginUserSafe() {
        Authentication auth = getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser) {
            return (LoginUser) auth.getPrincipal();
        }
        return null;
    }

    public static Long getUserId() {
        LoginUser u = getLoginUser();
        return u.getUserId();
    }

    public static Long getDeptId() {
        LoginUser u = getLoginUser();
        return u.getDeptId();
    }

    public static String getUsername() {
        return getLoginUser().getUsername();
    }

    public static String getNickName() {
        return getLoginUser().getNickName();
    }

    /** 安全取: 未登录返回空字符串 */
    public static String getUsernameSafe() {
        LoginUser u = getLoginUserSafe();
        return u == null ? "" : (u.getUsername() == null ? "" : u.getUsername());
    }
    public static Long getUserIdSafe() {
        LoginUser u = getLoginUserSafe();
        return u == null ? null : u.getUserId();
    }
    public static Long getDeptIdSafe() {
        LoginUser u = getLoginUserSafe();
        return u == null ? null : u.getDeptId();
    }

    public static Integer getDataScope() {
        LoginUser u = getLoginUser();
        return u.getDataScope() == null ? 4 : u.getDataScope();
    }

    /** 判断是否超级管理员(权限里有 *:*:* 或 userId == 1) */
    public static boolean isAdmin() {
        LoginUser u = getLoginUserSafe();
        if (u == null) return false;
        if (Long.valueOf(1L).equals(u.getUserId())) return true;
        return u.getPermissions() != null && u.getPermissions().contains("*:*:*");
    }

    public static void writeJson(HttpServletResponse resp, int code, String msg) throws IOException {
        writeJson(resp, R.fail(code, msg));
    }

    public static void writeJson(HttpServletResponse resp, Object data) throws IOException {
        resp.setStatus(HttpStatus.OK.value());
        resp.setContentType("application/json;charset=UTF-8");
        String json = JsonUtils.toJson(data);
        resp.getWriter().write(json);
    }
}
