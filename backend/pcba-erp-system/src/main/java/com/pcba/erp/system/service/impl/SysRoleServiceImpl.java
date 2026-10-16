package com.pcba.erp.system.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.system.entity.SysRole;
import com.pcba.erp.system.entity.SysUser;
import com.pcba.erp.system.mapper.SysRoleDeptMapper;
import com.pcba.erp.system.mapper.SysRoleMapper;
import com.pcba.erp.system.mapper.SysRoleMenuMapper;
import com.pcba.erp.system.mapper.SysUserRoleMapper;
import com.pcba.erp.system.service.ISysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements ISysRoleService {

    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public IPage<SysRole> selectRolePage(int pageNum, int pageSize, String roleName, String roleKey,
                                         Integer status, String beginTime, String endTime) {
        return baseMapper.selectRolePage(new Page<>(pageNum, pageSize), roleName, roleKey, status, beginTime, endTime);
    }

    @Override public List<SysRole> selectRolesByUserId(Long userId) { return baseMapper.selectRolesByUserId(userId); }

    @Override
    public Set<String> selectPermsByUserId(Long userId) {
        // 超管用户 ID=1
        if (Long.valueOf(1L).equals(userId)) {
            // 返回双份:
            //  '*'     → 兼容 RuoYi / 第三方组件 (前端 hasPerm 老写法: perms.includes('*') 即通配)
            //  '*:*:*' → 本项目 PermissionService 的超管通配符
            Set<String> superPerms = new HashSet<>(4);
            superPerms.add("*");
            superPerms.add("*:*:*");
            return superPerms;
        }
        // 菜单 perms + 角色级 perms (后者暂未启用,返回菜单权限)
        List<String> perms = baseMapper.selectPermsByUserId(userId);
        return new HashSet<>(perms == null ? Collections.emptyList() : perms);
    }

    @Override
    public Set<String> selectRoleKeysByUserId(Long userId) {
        List<SysRole> roles = selectRolesByUserId(userId);
        return roles.stream().map(SysRole::getRoleKey).collect(Collectors.toSet());
    }

    @Override
    public Integer resolveDataScope(Long userId) {
        if (Long.valueOf(1L).equals(userId)) return 1; // 超管=全部
        List<SysRole> roles = selectRolesByUserId(userId);
        // 取最大范围: 1>3>2>4
        int best = 4;
        for (SysRole r : roles) {
            if (r.getDataScope() == null) continue;
            int ds = r.getDataScope();
            // 优先级
            int weight = (ds == 1) ? 40 : (ds == 3) ? 30 : (ds == 2) ? 20 : 10;
            int curWeight = (best == 1) ? 40 : (best == 3) ? 30 : (best == 2) ? 20 : 10;
            if (weight > curWeight) best = ds;
        }
        return best;
    }

    @Override
    public boolean checkRoleKeyUnique(SysRole role) {
        Long id = role.getRoleId() == null ? -1L : role.getRoleId();
        SysRole ex = lambdaQuery().eq(SysRole::getRoleKey, role.getRoleKey()).one();
        return ex == null || ex.getRoleId().equals(id);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public int insertRole(SysRole role) {
        if (!checkRoleKeyUnique(role)) throw new ServiceException("角色权限标识已存在:" + role.getRoleKey());
        if (role.getDelFlag() == null) role.setDelFlag("0");
        if (role.getStatus() == null) role.setStatus(0);
        baseMapper.insert(role);
        Long rid = role.getRoleId();
        if (role.getMenuIds() != null && !role.getMenuIds().isEmpty()) {
            roleMenuMapper.batchInsert(rid, role.getMenuIds());
        }
        if (role.getDeptIds() != null && !role.getDeptIds().isEmpty() && Integer.valueOf(5).equals(role.getDataScope())) {
            // 自定义数据范围 (5) → 保存 sys_role_dept
            roleDeptMapper.batchInsert(rid, role.getDeptIds());
        }
        return 1;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public int updateRole(SysRole role) {
        Long rid = role.getRoleId();
        if (rid == null) throw new ServiceException("角色ID为空");
        if (Long.valueOf(1L).equals(rid) && Integer.valueOf(1).equals(role.getStatus())) {
            throw new ServiceException("超级管理员角色不能停用");
        }
        if (!checkRoleKeyUnique(role)) throw new ServiceException("角色权限标识已存在");
        baseMapper.updateById(role);
        // 重新分配菜单
        if (role.getMenuIds() != null) {
            roleMenuMapper.deleteByRoleId(rid);
            if (!role.getMenuIds().isEmpty()) roleMenuMapper.batchInsert(rid, role.getMenuIds());
        }
        // 重新分配部门 (仅自定义范围)
        if (role.getDataScope() != null) {
            roleDeptMapper.deleteByRoleId(rid);
            if (Integer.valueOf(5).equals(role.getDataScope()) && role.getDeptIds() != null && !role.getDeptIds().isEmpty()) {
                roleDeptMapper.batchInsert(rid, role.getDeptIds());
            }
        }
        return 1;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public int deleteRoleByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        if (ids.contains(1L)) throw new ServiceException("不能删除超级管理员角色");
        // 有用户绑定角色不允许删
        for (Long id : ids) {
            Long cnt = userRoleMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.pcba.erp.system.entity.SysUserRole>().eq(com.pcba.erp.system.entity.SysUserRole::getRoleId, id));
            if (cnt != null && cnt > 0) throw new ServiceException("角色下已分配用户,无法删除");
        }
        for (Long id : ids) {
            roleMenuMapper.deleteByRoleId(id);
            roleDeptMapper.deleteByRoleId(id);
        }
        return baseMapper.deleteBatchIds(ids);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public int authMenu(Long roleId, List<Long> menuIds) {
        if (roleId == null) throw new ServiceException("角色ID为空");
        roleMenuMapper.deleteByRoleId(roleId);
        if (menuIds != null && !menuIds.isEmpty()) roleMenuMapper.batchInsert(roleId, menuIds);
        return 1;
    }

    // 工具: 暴露 "角色下是否存在用户" 给 Controller 用 (避免无用 import)
    public static int dummy() { return 0; }
}
