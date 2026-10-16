package com.pcba.erp.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pcba.erp.system.entity.SysRole;

import java.util.List;
import java.util.Set;

public interface ISysRoleService extends IService<SysRole> {
    IPage<SysRole> selectRolePage(int pageNum, int pageSize, String roleName, String roleKey,
                                  Integer status, String beginTime, String endTime);

    /** 按用户ID查角色(含权限) */
    List<SysRole> selectRolesByUserId(Long userId);

    /** 用户拥有的权限标识集合 (含 *:*:* 超管) */
    Set<String> selectPermsByUserId(Long userId);

    /** 用户拥有的角色名集合(给Spring Security Authority用) */
    Set<String> selectRoleKeysByUserId(Long userId);

    /** 计算用户最大的数据范围(取角色最宽: 1>3>2>4) */
    Integer resolveDataScope(Long userId);

    /** 角色 key 唯一校验 */
    boolean checkRoleKeyUnique(SysRole role);

    /** 新增角色 (含 menuIds / deptIds) */
    int insertRole(SysRole role);
    /** 修改角色 (含 menuIds / deptIds) */
    int updateRole(SysRole role);
    /** 批量删除 */
    int deleteRoleByIds(List<Long> ids);
    /** 分配角色菜单权限 */
    int authMenu(Long roleId, List<Long> menuIds);
}
