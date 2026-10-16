package com.pcba.erp.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.system.entity.SysDept;
import com.pcba.erp.system.entity.SysRole;
import com.pcba.erp.system.service.ISysDeptService;
import com.pcba.erp.system.service.ISysMenuService;
import com.pcba.erp.system.service.ISysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final ISysRoleService roleService;
    private final ISysMenuService menuService;
    private final ISysDeptService deptService;

    @Operation(summary = "分页角色列表")
    @PreAuthorize("@ps.hasPermi('system:role:list')")
    @GetMapping("/list")
    public TableDataInfo<SysRole> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String roleKey,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime) {
        IPage<SysRole> p = roleService.selectRolePage(pageNum, pageSize, roleName, roleKey, status, beginTime, endTime);
        return TableDataInfo.of(p.getRecords(), p.getTotal());
    }

    @Operation(summary = "角色下拉选项")
    @PreAuthorize("@ps.hasPermi('system:user:list')")
    @GetMapping("/optionselect")
    public R<List<SysRole>> optionSelect() {
        List<SysRole> roles = SecurityUtils.isAdmin()
                ? roleService.lambdaQuery().eq(SysRole::getStatus, 0).orderByAsc(SysRole::getRoleSort).list()
                : roleService.selectRolesByUserId(SecurityUtils.getUserId());
        // 去掉敏感字段
        roles.forEach(r -> r.setPermissions(null));
        return R.ok(roles);
    }

    @Operation(summary = "角色详情(含已选菜单ID/部门ID)")
    @PreAuthorize("@ps.hasPermi('system:role:query')")
    @GetMapping("/{id}")
    public R<Map<String, Object>> get(@PathVariable Long id) {
        SysRole r = roleService.getById(id);
        if (r != null) {
            r.setMenuIds(menuService.selectMenuIdsByRoleId(id));
            // 自定义数据范围时查 deptIds: 简单全查, 后续可从 sys_role_dept 过滤
            // 这里先不给全查, 前端自定义范围时重新选即可.
        }
        // 菜单/部门树 (给 el-tree 全量)
        return R.ok(Map.of(
                "data", r == null ? new SysRole() : r,
                "menus", menuService.buildTree(menuService.selectMenuList(null, 0)),
                "depts", deptService.selectDeptList(null, 0)
        ));
    }

    @Operation(summary = "新增角色")
    @PreAuthorize("@ps.hasPermi('system:role:add')")
    @OperLog(title = "角色管理", businessType = 1)
    @PostMapping
    public R<Integer> add(@Validated @RequestBody SysRole role) {
        roleService.insertRole(role);
        return R.ok(1);
    }

    @Operation(summary = "修改角色")
    @PreAuthorize("@ps.hasPermi('system:role:edit')")
    @OperLog(title = "角色管理", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody SysRole role) {
        roleService.updateRole(role);
        return R.ok(1);
    }

    @Operation(summary = "批量删除角色")
    @PreAuthorize("@ps.hasPermi('system:role:remove')")
    @OperLog(title = "角色管理", businessType = 3)
    @DeleteMapping("/{ids}")
    public R<Integer> remove(@PathVariable List<Long> ids) {
        return R.ok(roleService.deleteRoleByIds(ids));
    }

    @Operation(summary = "分配角色菜单权限")
    @PreAuthorize("@ps.hasPermi('system:role:edit')")
    @OperLog(title = "角色管理", businessType = 4)
    @PutMapping("/auth-menu")
    public R<Integer> authMenu(@RequestBody Map<String, Object> body) {
        Long roleId = ((Number) body.get("roleId")).longValue();
        @SuppressWarnings("unchecked")
        List<Number> menuIds0 = (List<Number>) body.get("menuIds");
        List<Long> menuIds = menuIds0.stream().map(Number::longValue).toList();
        return R.ok(roleService.authMenu(roleId, menuIds));
    }

    @Operation(summary = "角色对应部门树(自定义数据范围时使用)")
    @PreAuthorize("@ps.hasPermi('system:role:list')")
    @GetMapping("/dept-tree/{roleId}")
    public R<List<SysDept>> deptTreeByRole(@PathVariable Long roleId) {
        // 简单返回部门树; 真正"已勾选项"由前端再调 /{id} 接口读取 deptCheckStrictly 等
        return R.ok(deptService.selectDeptList(null, 0));
    }
}
