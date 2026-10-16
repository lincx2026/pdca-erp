package com.pcba.erp.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 登录用户主体(UserDetails), 放入 Spring Security Context
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"authorities", "class"})
public class LoginUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;
    /** 部门ID */
    private Long deptId;
    /** 登录账号 */
    private String username;
    /** 密码 */
    private String password;
    /** 昵称 */
    private String nickName;
    /** 状态 1=正常, 0=停用 */
    private Integer status;
    /** 权限标识集合 (如 system:user:list) */
    private Set<String> permissions;
    /** 角色标识集合 (如 超级管理员) */
    private Set<String> roles;
    /** 角色数据权限范围(取最大范围): 1=全部 2=本部门 3=本部门及子部门 4=本人 */
    private Integer dataScope;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 角色: ROLE_xxx 前缀 (Spring Security hasRole 会自动加前缀)
        List<SimpleGrantedAuthority> a1 = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());
        // 权限标识: 原样
        List<SimpleGrantedAuthority> a2 = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        a1.addAll(a2);
        return a1;
    }

    @Override public boolean isAccountNonExpired()  { return true; }
    @Override public boolean isAccountNonLocked()   { return status != null && status == 1; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()            { return status != null && status == 1; }
}
