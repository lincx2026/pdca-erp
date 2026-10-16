package com.pcba.erp.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pcba.erp.system.entity.SysMenu;

import java.util.List;
import java.util.Set;

public interface ISysMenuService extends IService<SysMenu> {
    /** 当前用户可访问的菜单树（侧边栏/路由用：M/C） */
    List<SysMenu> selectMenuTreeByUserId(Long userId);

    /** 菜单管理页列表（保留 F 按钮，平铺） */
    List<SysMenu> selectMenuList(String menuName, Integer status);

    /** 角色分配菜单时：全量菜单树（M/C/F 都有，用于 el-tree 勾选） */
    List<SysMenu> selectRoleMenuTree();

    /** 通用树构建 */
    List<SysMenu> buildTree(List<SysMenu> all);

    /** 角色已分配 menuIds（含父级半选） */
    List<Long> selectMenuIdsByRoleId(Long roleId);

    /** 同级菜单名唯一校验 */
    boolean checkMenuNameUnique(SysMenu menu);

    /** 是否存在子菜单 */
    boolean hasChildByMenuId(Long menuId);

    /** 是否分配给了角色 */
    boolean checkMenuExistRole(Long menuId);

    /** 菜单管理"上级菜单"下拉：排除自身与子孙（防循环），仅目录 */
    List<SysMenu> selectMenuTreeExclude(Long excludeId);

    /** 当前用户的按钮级权限集合 */
    Set<String> selectMenuPermsByUserId(Long userId);
}
