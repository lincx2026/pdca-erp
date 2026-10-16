package com.pcba.erp.system.controller;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.system.entity.SysDept;
import com.pcba.erp.system.service.ISysDeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "部门管理")
@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final ISysDeptService deptService;

    @Operation(summary = "部门树列表")
    @PreAuthorize("@ps.hasPermi('system:dept:list')")
    @GetMapping("/list")
    public R<List<SysDept>> list(
            @RequestParam(required = false) String deptName,
            @RequestParam(required = false) Integer status) {
        return R.ok(deptService.selectDeptList(deptName, status));
    }

    @Operation(summary = "部门详情")
    @PreAuthorize("@ps.hasPermi('system:dept:query')")
    @GetMapping("/{id}")
    public R<SysDept> get(@PathVariable Long id) {
        return R.ok(deptService.getById(id));
    }

    @Operation(summary = "获取部门下拉树(排除某部门及子级,防止循环)")
    @PreAuthorize("@ps.hasPermi('system:dept:list')")
    @GetMapping("/exclude-tree")
    public R<List<SysDept>> excludeTree(@RequestParam(required = false) Long excludeId) {
        List<SysDept> all = deptService.selectDeptList(null, null);
        if (excludeId != null) {
            // 过滤: 不展示 excludeId 本身和它的后代
            all = excludeAndChildren(all, excludeId);
        }
        return R.ok(all);
    }

    @Operation(summary = "新增部门")
    @PreAuthorize("@ps.hasPermi('system:dept:add')")
    @OperLog(title = "部门管理", businessType = 1)
    @PostMapping
    public R<Integer> add(@Validated @RequestBody SysDept dept) {
        return deptService.save(dept) ? R.ok(1) : R.fail("新增失败");
    }

    @Operation(summary = "修改部门")
    @PreAuthorize("@ps.hasPermi('system:dept:edit')")
    @OperLog(title = "部门管理", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody SysDept dept) {
        if (dept.getDeptId() == null) return R.fail("部门ID不能为空");
        if (dept.getParentId() != null && dept.getParentId().equals(dept.getDeptId())) return R.fail("父级不能是自己");
        return deptService.updateById(dept) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除部门")
    @PreAuthorize("@ps.hasPermi('system:dept:remove')")
    @OperLog(title = "部门管理", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        if (Long.valueOf(100L).equals(id)) throw new ServiceException("不能删除集团根部门");
        if (deptService.hasChildByDeptId(id)) throw new ServiceException("存在下级部门,不能删除");
        if (deptService.checkDeptExistUser(id)) throw new ServiceException("部门下存在用户,不能删除");
        return deptService.removeById(id) ? R.ok(1) : R.fail("删除失败");
    }

    // =============== 私有 ===============
    private List<SysDept> excludeAndChildren(List<SysDept> root, Long excludeId) {
        // 收集要排除的节点ID(含后代), 用迭代过滤
        java.util.Set<Long> ids = new java.util.HashSet<>();
        collect(null, root, excludeId, ids);
        // 递归删除 root 中的 children
        filterTree(root, ids);
        return root;
    }
    private void collect(SysDept cur, List<SysDept> children, Long target, java.util.Set<Long> ids) {
        if (children == null) return;
        for (SysDept c : children) {
            if (target.equals(c.getDeptId())) {
                ids.add(c.getDeptId());
                // 它所有后代也加入
                addAll(c, ids);
            }
            collect(c, c.getChildren(), target, ids);
        }
    }
    private void addAll(SysDept n, java.util.Set<Long> ids) {
        if (n.getChildren() == null) return;
        for (SysDept c : n.getChildren()) { ids.add(c.getDeptId()); addAll(c, ids); }
    }
    private void filterTree(List<SysDept> nodes, java.util.Set<Long> ids) {
        if (nodes == null) return;
        nodes.removeIf(n -> ids.contains(n.getDeptId()));
        for (SysDept n : nodes) filterTree(n.getChildren(), ids);
    }
}
