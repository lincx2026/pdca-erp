package com.pcba.erp.pdm.controller;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.pdm.entity.PdmMaterialCategory;
import com.pcba.erp.pdm.service.IPdmMaterialCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料分类 Controller - 树形 CRUD
 */
@Tag(name = "物料分类管理")
@RestController
@RequestMapping("/pdm/category")
@RequiredArgsConstructor
public class PdmMaterialCategoryController {

    private final IPdmMaterialCategoryService categoryService;

    @Operation(summary = "分类树列表 (默认含子节点)")
    @PreAuthorize("@ps.hasPermi('pdm:category:list')")
    @GetMapping("/list")
    public R<List<PdmMaterialCategory>> list(
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "true") boolean withMaterialCount) {
        List<PdmMaterialCategory> tree = categoryService.selectCategoryTree(categoryName, status);
        if (withMaterialCount) categoryService.fillMaterialCount(tree);
        return R.ok(tree);
    }

    @Operation(summary = "分类扁平列表 (给物料页左侧筛选)")
    @PreAuthorize("@ps.hasPermi('pdm:category:list')")
    @GetMapping("/flat")
    public R<List<PdmMaterialCategory>> flat(
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) Integer status) {
        return R.ok(categoryService.selectCategoryList(categoryName, status));
    }

    @Operation(summary = "分类详情")
    @PreAuthorize("@ps.hasPermi('pdm:category:query')")
    @GetMapping("/{id}")
    public R<PdmMaterialCategory> get(@PathVariable Long id) {
        return R.ok(categoryService.getById(id));
    }

    @Operation(summary = "分类下拉树 (排除某节点及子级, 防止循环父级)")
    @PreAuthorize("@ps.hasPermi('pdm:category:list')")
    @GetMapping("/exclude-tree")
    public R<List<PdmMaterialCategory>> excludeTree(@RequestParam(required = false) Long excludeId) {
        return R.ok(categoryService.excludeTree(excludeId));
    }

    @Operation(summary = "新增分类")
    @PreAuthorize("@ps.hasPermi('pdm:category:add')")
    @OperLog(title = "物料分类", businessType = 1)
    @PostMapping
    public R<Integer> add(@Validated @RequestBody PdmMaterialCategory category) {
        return categoryService.save(category) ? R.ok(1) : R.fail("新增失败");
    }

    @Operation(summary = "修改分类")
    @PreAuthorize("@ps.hasPermi('pdm:category:edit')")
    @OperLog(title = "物料分类", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody PdmMaterialCategory category) {
        if (category.getCategoryId() == null) return R.fail("分类ID不能为空");
        if (category.getParentId() != null && category.getParentId().equals(category.getCategoryId())) {
            return R.fail("父级不能是自己");
        }
        return categoryService.updateById(category) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除分类")
    @PreAuthorize("@ps.hasPermi('pdm:category:remove')")
    @OperLog(title = "物料分类", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return categoryService.removeById(id) ? R.ok(1) : R.fail("删除失败");
    }
}
