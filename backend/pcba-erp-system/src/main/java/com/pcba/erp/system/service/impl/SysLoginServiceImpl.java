package com.pcba.erp.system.service.impl;

import com.pcba.erp.common.core.utils.JsonUtils;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.common.security.JwtAuthenticationTokenFilter;
import com.pcba.erp.common.security.JwtTokenUtil;
import com.pcba.erp.common.security.LoginUser;
import com.pcba.erp.system.entity.SysUser;
import com.pcba.erp.system.service.ISysLoginService;
import com.pcba.erp.system.service.ISysRoleService;
import com.pcba.erp.system.service.ISysUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysLoginServiceImpl implements ISysLoginService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final StringRedisTemplate redisTemplate;
    private final ISysUserService userService;
    private final ISysRoleService roleService;

    @Value("${pcba.jwt.expire-minutes:720}")
    private long expireMinutes;

    @Override
    public String login(String username, String password) {
        // AuthenticationManager 会回调 UserDetailsServiceImpl.loadUserByUsername → 通过 SysUser 查 DB, PasswordEncoder 比对 BCrypt
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(username, password);
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(token);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "用户名或密码错误" : e.getMessage();
            if (msg.contains("Bad credentials") || msg.contains("密码不匹配")) msg = "用户名或密码错误";
            if (msg.contains("Locked")) msg = "账号已停用,请联系管理员";
            if (msg.contains("未找到") || msg.contains("UsernameNotFound")) msg = "账号不存在";
            throw new ServiceException(401, msg);
        }
        LoginUser loginUser = (LoginUser) auth.getPrincipal();
        // 生成 JWT
        String jwt = jwtTokenUtil.generateToken(loginUser.getUserId(), loginUser.getUsername());
        // 存 Redis
        redisTemplate.opsForValue().set(JwtAuthenticationTokenFilter.LOGIN_TOKEN_PREFIX + loginUser.getUserId(),
                JsonUtils.toJson(loginUser), expireMinutes, TimeUnit.MINUTES);
        // 记录登录 IP/时间
        String ip = currentIp();
        userService.recordLoginInfo(loginUser.getUserId(), ip);
        return jwt;
    }

    @Override
    public LoginUser buildLoginUser(String username) {
        SysUser u = userService.selectUserByUserName(username);
        if (u == null) throw new ServiceException(401, "账号不存在: " + username);
        if (u.getStatus() != null && u.getStatus() == 0) throw new ServiceException(401, "账号已停用");
        Set<String> perms = roleService.selectPermsByUserId(u.getUserId());
        Set<String> roleKeys = roleService.selectRoleKeysByUserId(u.getUserId());
        Integer ds = roleService.resolveDataScope(u.getUserId());
        return new LoginUser(u.getUserId(), u.getDeptId(), u.getUserName(), u.getPassword(),
                u.getNickName(), u.getStatus(), perms, roleKeys, ds);
    }

    private static String currentIp() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr == null) return "";
        HttpServletRequest r = attr.getRequest();
        String[] heads = {"X-Forwarded-For","X-Real-IP","Proxy-Client-IP","WL-Proxy-Client-IP"};
        for (String h : heads) {
            String v = r.getHeader(h);
            if (v != null && !v.isEmpty() && !"unknown".equalsIgnoreCase(v)) {
                int idx = v.indexOf(',');
                return idx < 0 ? v : v.substring(0, idx).trim();
            }
        }
        return r.getRemoteAddr();
    }

    // 保留: SecurityUtils 静态 import 避免编译告警
    static { SecurityUtils.encodePassword("nop"); }
}
