package com.pcba.erp;

import com.pcba.erp.system.entity.SysMenu;
import com.pcba.erp.system.entity.SysRole;
import com.pcba.erp.system.mapper.*;
import com.pcba.erp.system.service.impl.SysMenuServiceImpl;
import com.pcba.erp.system.service.impl.SysRoleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SuperAdminRoleTest {
    private SysRoleServiceImpl roles;
    private SysRoleMapper roleMapper;
    private SysMenuServiceImpl menus;
    private SysMenuMapper menuMapper;

    @BeforeEach
    void setUp() {
        roles=new SysRoleServiceImpl(mock(SysRoleMenuMapper.class),mock(SysRoleDeptMapper.class),
                mock(SysUserRoleMapper.class),mock(StringRedisTemplate.class));
        roleMapper=mock(SysRoleMapper.class);
        ReflectionTestUtils.setField(roles,"baseMapper",roleMapper);
        menus=new SysMenuServiceImpl(mock(SysRoleMenuMapper.class),roles);
        menuMapper=mock(SysMenuMapper.class);
        ReflectionTestUtils.setField(menus,"baseMapper",menuMapper);
    }

    @Test
    void nonRootUserWithAdminRoleGetsSuperPermissions() {
        role(1008L,"admin",4);
        assertEquals(Set.of("*","*:*:*"),roles.selectPermsByUserId(1008L));
    }

    @Test
    void nonRootUserWithAdminRoleGetsAllDataScope() {
        role(1008L,"admin",4);
        assertEquals(1,roles.resolveDataScope(1008L));
    }

    @Test
    void adminRoleGetsAllMenusWithoutExplicitDirectoryBindings() {
        role(1008L,"admin",1);
        SysMenu root=new SysMenu();root.setMenuId(10L);root.setParentId(0L);root.setMenuType("M");
        when(menuMapper.selectMenuTreeAll()).thenReturn(List.of(root));
        when(menuMapper.selectMenuTreeByUserId(1008L)).thenReturn(List.of());
        assertEquals(List.of(root),menus.selectMenuTreeByUserId(1008L));
        verify(menuMapper,never()).selectMenuTreeByUserId(1008L);
    }

    @Test
    void ordinaryRoleKeepsAssignedPermissionsAndMenus() {
        role(42L,"common",2);
        when(roleMapper.selectPermsByUserId(42L)).thenReturn(List.of("system:user:list"));
        assertEquals(Set.of("system:user:list"),roles.selectPermsByUserId(42L));
        assertEquals(2,roles.resolveDataScope(42L));
        menus.selectMenuTreeByUserId(42L);
        verify(menuMapper).selectMenuTreeByUserId(42L);
        verify(menuMapper,never()).selectMenuTreeAll();
    }

    @Test
    void rootAccountRetainsBuiltInSuperPermissions() {
        assertEquals(Set.of("*","*:*:*"),roles.selectPermsByUserId(1L));
        assertEquals(1,roles.resolveDataScope(1L));
    }

    private void role(long userId,String key,int scope) {
        SysRole role=new SysRole();role.setRoleKey(key);role.setStatus(1);role.setDelFlag("0");role.setDataScope(scope);
        when(roleMapper.selectRolesByUserId(userId)).thenReturn(List.of(role));
    }
}
