package com.pcba.erp.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * SpEL 权限校验服务:
 * - @PreAuthorize("@ps.hasPermi('pdm:bom:list')")
 * - 超级管理员(perm 包含 *:*:*) 直接放行
 * - 普通用户检查具体 perm 是否存在
 */
@Slf4j
@Component("ps")
public class PermissionService {

    public static final String SUPER_PERM = "*:*:*";

    public boolean hasPermi(String perm) {
        if (perm == null || perm.isEmpty()) return false;
        LoginUser u = current();
        if (u == null) return false;
        Set<String> perms = u.getPermissions();
        if (perms == null) return false;
        return perms.contains(SUPER_PERM) || perms.contains(perm);
    }

    public boolean hasAnyPermi(String... perms) {
        if (perms == null || perms.length == 0) return false;
        LoginUser u = current();
        if (u == null) return false;
        Set<String> userPerms = u.getPermissions();
        if (userPerms == null) return false;
        if (userPerms.contains(SUPER_PERM)) return true;
        for (String p : perms) {
            if (p != null && userPerms.contains(p)) return true;
        }
        return false;
    }

    public boolean hasRole(String roleKey) {
        if (roleKey == null || roleKey.isEmpty()) return false;
        LoginUser u = current();
        if (u == null) return false;
        Set<String> roles = u.getRoles();
        if (roles == null) return false;
        return roles.contains("admin") || roles.contains(roleKey);
    }

    private LoginUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object p = auth.getPrincipal();
        return p instanceof LoginUser ? (LoginUser) p : null;
    }
}
