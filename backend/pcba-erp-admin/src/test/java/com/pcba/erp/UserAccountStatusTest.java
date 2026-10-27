package com.pcba.erp;

import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.system.entity.SysUser;
import com.pcba.erp.system.mapper.SysDeptMapper;
import com.pcba.erp.system.mapper.SysUserMapper;
import com.pcba.erp.system.mapper.SysUserRoleMapper;
import com.pcba.erp.system.service.ISysRoleService;
import com.pcba.erp.system.service.ISysUserService;
import com.pcba.erp.system.service.impl.SysUserServiceImpl;
import com.pcba.erp.system.service.impl.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserAccountStatusTest {
    private SysUserServiceImpl service;
    private SysUserMapper mapper;

    @BeforeEach
    void setUp() {
        service=spy(new SysUserServiceImpl(mock(SysUserRoleMapper.class),mock(SysDeptMapper.class)));
        mapper=mock(SysUserMapper.class);
        ReflectionTestUtils.setField(service,"baseMapper",mapper);
        doReturn(true).when(service).checkUserNameUnique(any());
        doReturn(true).when(service).checkPhoneUnique(any());
        doReturn(true).when(service).checkEmailUnique(any());
        when(mapper.updateById(any(SysUser.class))).thenReturn(1);
    }

    @Test
    void editingSuperAdminCannotDisableLogin() {
        SysUser user=new SysUser();user.setUserId(1L);user.setStatus(0);
        assertThrows(ServiceException.class,()->service.updateUser(user));
        verify(mapper,never()).updateById(any(SysUser.class));
    }

    @Test
    void statusEndpointCannotDisableSuperAdmin() {
        assertThrows(ServiceException.class,()->service.updateUserStatus(1L,0));
        verify(mapper,never()).updateById(any(SysUser.class));
    }

    @Test
    void enabledSuperAdminCanBeEditedAndReenabled() {
        SysUser user=new SysUser();user.setUserId(1L);user.setStatus(1);
        assertEquals(1,service.updateUser(user));
        assertEquals(1,service.updateUserStatus(1L,1));
    }

    @Test
    void newlyCreatedAccountDefaultsToEnabled() {
        SysUser user=new SysUser();user.setUserName("status-test");user.setPassword("test-password-only");
        service.insertUser(user);
        assertEquals(1,user.getStatus());
        assertTrue(new BCryptPasswordEncoder().matches("test-password-only",user.getPassword()));
    }

    @Test
    void disabledAccountIsNotMisreportedAsWrongPassword() {
        ISysUserService users=mock(ISysUserService.class);
        SysUser disabled=new SysUser();disabled.setUserId(1L);disabled.setUserName("admin");disabled.setStatus(0);
        disabled.setPassword(new BCryptPasswordEncoder(4).encode("test-password-only"));
        when(users.selectUserByUserName("admin")).thenReturn(disabled);
        DaoAuthenticationProvider provider=new DaoAuthenticationProvider();
        provider.setUserDetailsService(new UserDetailsServiceImpl(users,mock(ISysRoleService.class)));
        provider.setPasswordEncoder(new BCryptPasswordEncoder());
        AuthenticationException error=assertThrows(AuthenticationException.class,()->provider.authenticate(
                new UsernamePasswordAuthenticationToken("admin","test-password-only")));
        assertTrue(error.getMessage().contains("账号已停用"));
    }
}
