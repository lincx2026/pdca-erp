package com.pcba.erp.system.service.impl;

import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.common.security.LoginUser;
import com.pcba.erp.system.entity.SysUser;
import com.pcba.erp.system.service.ISysRoleService;
import com.pcba.erp.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Spring Security 的 UserDetailsService 实现:
 * - 通过用户名返回 LoginUser
 * - BCrypt 密码校验由 Spring Security DaoAuthenticationProvider 自动做
 *
 * 直接依赖 ISysUserService + ISysRoleService 构造 LoginUser,
 * 不引入 ISysLoginService 以避免与 AuthenticationManager 形成循环依赖
 * (SysLoginService → AuthenticationManager → 本类 → SysLoginService)
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final ISysUserService userService;
    private final ISysRoleService roleService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            SysUser u = userService.selectUserByUserName(username);
            if (u == null) throw new UsernameNotFoundException("账号不存在: " + username);
            if (u.getStatus() != null && u.getStatus() == 0) throw new DisabledException("账号已停用,请联系管理员");
            Set<String> perms = roleService.selectPermsByUserId(u.getUserId());
            Set<String> roleKeys = roleService.selectRoleKeysByUserId(u.getUserId());
            Integer ds = roleService.resolveDataScope(u.getUserId());
            return new LoginUser(u.getUserId(), u.getDeptId(), u.getUserName(), u.getPassword(),
                    u.getNickName(), u.getStatus(), perms, roleKeys, ds);
        } catch (UsernameNotFoundException | DisabledException e) {
            throw e;
        } catch (ServiceException e) {
            throw new UsernameNotFoundException(e.getMessage());
        } catch (Exception e) {
            throw new UsernameNotFoundException(e.getMessage() == null ? "账号不存在" : e.getMessage());
        }
    }
}
