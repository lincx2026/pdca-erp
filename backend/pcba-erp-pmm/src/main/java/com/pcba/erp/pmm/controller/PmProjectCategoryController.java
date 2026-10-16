package com.pcba.erp.pmm.controller;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.pmm.entity.PmProjectCategory;
import com.pcba.erp.pmm.service.IPmProjectCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "项目分类")
@RestController
@RequestMapping("/pm/project-category")
@RequiredArgsConstructor
public class PmProjectCategoryController {

    private final IPmProjectCategoryService service;

    @Operation(summary = "分类树形列表")
    @PreAuthorize("@ps.hasPermi('pm:category:list')")
    @GetMapping("/tree")
    public R<List<PmProjectCategory>> tree() {
        return R.ok(service.tree());
    }

    @Operation(summary = "分类平铺列表")
    @PreAuthorize("@ps.hasPermi('pm:category:list')")
    @GetMapping("/list")
    public R<List<PmProjectCategory>> list() {
        return R.ok(service.list());
    }

    @Operation(summary = "分类详情")
    @PreAuthorize("@ps.hasPermi('pm:category:list')")
    @GetMapping("/{id}")
    public R<PmProjectCategory> get(@PathVariable Long id) {
        return R.ok(service.getDetail(id));
    }

    @Operation(summary = "新增分类")
    @PreAuthorize("@ps.hasPermi('pm:category:save')")
    @OperLog(title = "项目分类", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody PmProjectCategory entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改分类")
    @PreAuthorize("@ps.hasPermi('pm:category:save')")
    @OperLog(title = "项目分类", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody PmProjectCategory entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除分类")
    @PreAuthorize("@ps.hasPermi('pm:category:remove')")
    @OperLog(title = "项目分类", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }
}
