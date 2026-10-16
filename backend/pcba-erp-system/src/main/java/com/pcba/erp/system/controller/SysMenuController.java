package com.pcba.erp.system.controller;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.system.entity.SysMenu;
import com.pcba.erp.system.service.ISysMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "菜单管理")
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final ISysMenuService menuService;

    @Operation(summary = "菜单列表（平铺，含按钮）", description = "用于菜单管理表格，返回原始平铺列表，前端自行构建树")
    @PreAuthorize("@ps.hasPermi('system:menu:list')")
    @GetMapping("/list")
    public R<List<SysMenu>> list(
            @RequestParam(required = false) String menuName,
            @RequestParam(required = false) Integer status) {
        return R.ok(menuService.selectMenuList(menuName, status));
    }

    @Operation(summary = "角色分配菜单树（不含按钮，仅M/C目录菜单）")
    @PreAuthorize("@ps.hasPermi('system:menu:list')")
    @GetMapping("/tree-select")
    public R<List<SysMenu>> treeSelect() {
        return R.ok(menuService.buildTree(menuService.selectMenuList(null, 0).stream()
                .filter(m -> !"F".equals(m.getMenuType())).toList()));
    }

    @Operation(summary = "菜单管理上级树（排除自身及子孙,仅目录）")
    @PreAuthorize("@ps.hasPermi('system:menu:list')")
    @GetMapping("/exclude-tree")
    public R<List<SysMenu>> excludeTree(@RequestParam(required = false) Long excludeId) {
        return R.ok(menuService.selectMenuTreeExclude(excludeId));
    }

    @Operation(summary = "角色已分配的菜单树与勾选IDs")
    @PreAuthorize("@ps.hasPermi('system:role:query')")
    @GetMapping("/role-menu-tree/{roleId}")
    public R<Map<String, Object>> roleMenuTree(@PathVariable Long roleId) {
        List<SysMenu> menus = menuService.selectRoleMenuTree();
        List<Long> checked = menuService.selectMenuIdsByRoleId(roleId);
        return R.ok(Map.of("menus", menus, "checkedKeys", checked));
    }

    @Operation(summary = "菜单详情")
    @PreAuthorize("@ps.hasPermi('system:menu:query')")
    @GetMapping("/{id}")
    public R<SysMenu> get(@PathVariable Long id) {
        return R.ok(menuService.getById(id));
    }

    @Operation(summary = "新增菜单")
    @PreAuthorize("@ps.hasPermi('system:menu:add')")
    @OperLog(title = "菜单管理", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody SysMenu menu) {
        if (!menuService.checkMenuNameUnique(menu)) throw new ServiceException("同级菜单名称重复");
        menuService.save(menu);
        return R.ok(menu.getMenuId());
    }

    @Operation(summary = "修改菜单")
    @PreAuthorize("@ps.hasPermi('system:menu:edit')")
    @OperLog(title = "菜单管理", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody SysMenu menu) {
        if (menu.getMenuId() == null) return R.fail("菜单ID为空");
        if (!menuService.checkMenuNameUnique(menu)) throw new ServiceException("同级菜单名称重复");
        return menuService.updateById(menu) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除菜单")
    @PreAuthorize("@ps.hasPermi('system:menu:remove')")
    @OperLog(title = "菜单管理", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        if (menuService.hasChildByMenuId(id)) throw new ServiceException("存在子菜单，不能删除");
        if (menuService.checkMenuExistRole(id)) throw new ServiceException("菜单已分配角色，不能删除");
        return menuService.removeById(id) ? R.ok(1) : R.fail("删除失败");
    }
}
