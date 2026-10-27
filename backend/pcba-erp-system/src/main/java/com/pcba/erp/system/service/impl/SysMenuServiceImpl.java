package com.pcba.erp.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.system.entity.SysMenu;
import com.pcba.erp.system.entity.SysRoleMenu;
import com.pcba.erp.system.mapper.SysMenuMapper;
import com.pcba.erp.system.mapper.SysRoleMenuMapper;
import com.pcba.erp.system.service.ISysMenuService;
import com.pcba.erp.system.service.ISysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements ISysMenuService {

    private final SysRoleMenuMapper roleMenuMapper;
    private final ISysRoleService roleService;

    @Override
    public List<SysMenu> selectMenuTreeByUserId(Long userId) {
        List<SysMenu> flat = roleService.isSuperAdmin(userId)
                ? baseMapper.selectMenuTreeAll()
                : baseMapper.selectMenuTreeByUserId(userId);
        return buildTree(flat);
    }

    @Override
    public List<SysMenu> selectMenuList(String menuName, Integer status) {
        return baseMapper.selectMenuList(menuName, status);
    }

    @Override
    public List<SysMenu> selectRoleMenuTree() {
        // 角色权限树：含 M/C/F 全量、仅正常状态(status=0)，按原排序
        List<SysMenu> flat = baseMapper.selectMenuList(null, 0);
        return buildTree(flat);
    }

    @Override
    public List<SysMenu> buildTree(List<SysMenu> all) {
        if (all == null || all.isEmpty()) return Collections.emptyList();
        Map<Long, List<SysMenu>> groupByParent = all.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? 0L : m.getParentId()));
        // children 内按 order_num 再排一次
        for (SysMenu m : all) {
            List<SysMenu> sons = groupByParent.getOrDefault(m.getMenuId(), null);
            if (sons != null) {
                sons.sort(Comparator.comparingInt(s -> s.getOrderNum() == null ? 0 : s.getOrderNum()));
            }
            m.setChildren(sons);
        }
        List<SysMenu> roots = groupByParent.getOrDefault(0L, new ArrayList<>());
        roots.sort(Comparator.comparingInt(s -> s.getOrderNum() == null ? 0 : s.getOrderNum()));
        return roots;
    }

    @Override public List<Long> selectMenuIdsByRoleId(Long roleId) {
        return baseMapper.selectMenuIdsByRoleId(roleId);
    }

    @Override
    public boolean checkMenuNameUnique(SysMenu menu) {
        Long pid = menu.getParentId() == null ? 0L : menu.getParentId();
        Long id = menu.getMenuId() == null ? -1L : menu.getMenuId();
        SysMenu ex = lambdaQuery().eq(SysMenu::getParentId, pid).eq(SysMenu::getMenuName, menu.getMenuName()).one();
        return ex == null || ex.getMenuId().equals(id);
    }

    @Override public boolean hasChildByMenuId(Long menuId) {
        return lambdaQuery().eq(SysMenu::getParentId, menuId).count() > 0;
    }

    @Override
    public boolean checkMenuExistRole(Long menuId) {
        Long c = roleMenuMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, menuId));
        return c != null && c > 0;
    }

    @Override
    public List<SysMenu> selectMenuTreeExclude(Long excludeId) {
        List<SysMenu> flat = baseMapper.selectMenuTreeExclude(excludeId);
        return buildTree(flat);
    }

    @Override
    public Set<String> selectMenuPermsByUserId(Long userId) {
        if (roleService.isSuperAdmin(userId)) return Collections.singleton("*:*:*");
        List<String> p = baseMapper.selectMenuPermsByUserId(userId);
        return new HashSet<>(p == null ? Collections.emptyList() : p);
    }

    @Override
    public boolean save(SysMenu menu) {
        if (!checkMenuNameUnique(menu)) throw new ServiceException("同层级菜单名已存在");
        if (!StringUtils.hasText(menu.getMenuType())) menu.setMenuType("C");
        if (menu.getParentId() == null) menu.setParentId(0L);
        if (menu.getOrderNum() == null) menu.setOrderNum(0);
        if (menu.getIsCache() == null) menu.setIsCache(0);
        if (menu.getIsFrame() == null) menu.setIsFrame(0);
        if (menu.getVisible() == null) menu.setVisible(0);
        if (menu.getStatus() == null) menu.setStatus(0);
        return super.save(menu);
    }

    @Override
    public boolean updateById(SysMenu menu) {
        if (menu.getMenuId() != null && menu.getParentId() != null) {
            Long newParentId = menu.getParentId();
            if (menu.getMenuId().equals(newParentId)) throw new ServiceException("父级菜单不能是自己");
            List<Long> descendants = descendants(menu.getMenuId());
            if (descendants.contains(newParentId)) throw new ServiceException("父级菜单不能设置为自己的子菜单");
        }
        return super.updateById(menu);
    }

    private List<Long> descendants(Long parentId) {
        List<Long> ids = new ArrayList<>();
        List<SysMenu> sons = lambdaQuery().eq(SysMenu::getParentId, parentId).list();
        for (SysMenu s : sons) {
            ids.add(s.getMenuId());
            ids.addAll(descendants(s.getMenuId()));
        }
        return ids;
    }
}
