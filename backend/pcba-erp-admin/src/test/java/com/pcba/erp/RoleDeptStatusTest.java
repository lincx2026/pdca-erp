package com.pcba.erp;

import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.system.entity.SysDept;
import com.pcba.erp.system.entity.SysRole;
import com.pcba.erp.system.entity.SysUserRole;
import com.pcba.erp.system.mapper.*;
import com.pcba.erp.system.service.impl.SysDeptServiceImpl;
import com.pcba.erp.system.service.impl.SysRoleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoleDeptStatusTest {
    private SysRoleServiceImpl roles;
    private SysDeptServiceImpl depts;
    private SysRoleMapper roleMapper;
    private SysDeptMapper deptMapper;
    private SysUserRoleMapper userRoleMapper;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        userRoleMapper=mock(SysUserRoleMapper.class);
        redis=mock(StringRedisTemplate.class);
        roles=spy(new SysRoleServiceImpl(mock(SysRoleMenuMapper.class),mock(SysRoleDeptMapper.class),userRoleMapper,redis));
        roleMapper=mock(SysRoleMapper.class);
        ReflectionTestUtils.setField(roles,"baseMapper",roleMapper);
        doReturn(true).when(roles).checkRoleKeyUnique(any());
        when(roleMapper.insert(any(SysRole.class))).thenReturn(1);
        when(roleMapper.updateById(any(SysRole.class))).thenReturn(1);
        depts=spy(new SysDeptServiceImpl(mock(SysUserMapper.class)));
        deptMapper=mock(SysDeptMapper.class);
        ReflectionTestUtils.setField(depts,"baseMapper",deptMapper);
        doReturn(true).when(depts).checkDeptNameUnique(any());
        when(deptMapper.insert(any(SysDept.class))).thenReturn(1);
    }

    @Test
    void newRoleDefaultsToEnabled() {
        SysRole role=new SysRole();role.setRoleKey("status-test");
        assertEquals(1,roles.insertRole(role));
        assertEquals(1,role.getStatus());
    }

    @Test
    void superAdminRoleCannotBeDisabled() {
        SysRole role=new SysRole();role.setRoleId(1L);role.setStatus(0);
        assertThrows(ServiceException.class,()->roles.updateRole(role));
        verify(roleMapper,never()).updateById(any(SysRole.class));
    }

    @Test
    void superAdminRoleCanBeEditedWhileEnabled() {
        SysRole role=new SysRole();role.setRoleId(1L);role.setStatus(1);
        assertEquals(1,roles.updateRole(role));
    }

    @Test
    void newDepartmentDefaultsToEnabled() {
        SysDept dept=new SysDept();dept.setParentId(0L);
        assertTrue(depts.save(dept));
        assertEquals(1,dept.getStatus());
    }

    @Test
    void enabledParentAllowsCreatingChildren() {
        parent(100L,1);
        SysDept child=new SysDept();child.setParentId(100L);
        assertTrue(depts.save(child));
        assertEquals("0,100",child.getAncestors());
    }

    @Test
    void disabledParentRejectsCreatingChildren() {
        parent(100L,0);
        SysDept child=new SysDept();child.setParentId(100L);
        assertThrows(ServiceException.class,()->depts.save(child));
        verify(deptMapper,never()).insert(any(SysDept.class));
    }

    @Test
    void cannotMoveDepartmentUnderDisabledParent() {
        parent(100L,0);
        SysDept existing=new SysDept();existing.setDeptId(101L);existing.setParentId(0L);
        when(deptMapper.selectById(101L)).thenReturn(existing);
        SysDept child=new SysDept();child.setDeptId(101L);child.setParentId(100L);
        assertThrows(ServiceException.class,()->depts.updateById(child));
        verify(deptMapper,never()).updateById(any(SysDept.class));
    }

    @Test
    void filteredDepartmentRemainsVisibleWhenParentDoesNotMatch() {
        SysDept child=new SysDept();child.setDeptId(101L);child.setParentId(100L);child.setStatus(0);
        when(deptMapper.selectDeptList(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.eq(0),any())).thenReturn(java.util.List.of(child));
        assertEquals(java.util.List.of(child),depts.selectDeptList(null,0));
    }

    @Test
    void statusChangeInvalidatesOnlyRelatedSessionsAfterCommit() {
        SysRole previous=new SysRole();previous.setRoleId(2L);previous.setStatus(1);
        when(roleMapper.selectById(2L)).thenReturn(previous);
        SysUserRole binding=new SysUserRole();binding.setUserId(42L);binding.setRoleId(2L);
        when(userRoleMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(java.util.List.of(binding));
        SysRole changed=new SysRole();changed.setRoleId(2L);changed.setStatus(0);
        TransactionSynchronizationManager.initSynchronization();
        try {
            roles.updateRole(changed);
            verifyNoInteractions(redis);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(redis).delete(java.util.List.of("login_tokens:42"));
            verifyNoMoreInteractions(redis);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test
    void rolledBackStatusChangeKeepsSessions() {
        SysRole previous=new SysRole();previous.setRoleId(2L);previous.setStatus(1);
        when(roleMapper.selectById(2L)).thenReturn(previous);
        SysUserRole binding=new SysUserRole();binding.setUserId(42L);binding.setRoleId(2L);
        when(userRoleMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(java.util.List.of(binding));
        SysRole changed=new SysRole();changed.setRoleId(2L);changed.setStatus(0);
        TransactionSynchronizationManager.initSynchronization();
        try {
            roles.updateRole(changed);
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(redis);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    private void parent(long id,int status) {
        SysDept parent=new SysDept();parent.setDeptId(id);parent.setStatus(status);parent.setAncestors("0");
        when(deptMapper.selectById(id)).thenReturn(parent);
    }
}
