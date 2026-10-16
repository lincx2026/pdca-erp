package com.pcba.erp.pdm.controller;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.pdm.entity.PdmMaterialSupplier;
import com.pcba.erp.pdm.service.IPdmMaterialSupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "物料-供应商阶梯价")
@RestController
@RequestMapping("/pdm/material-supplier")
@RequiredArgsConstructor
public class PdmMaterialSupplierController {

    private final IPdmMaterialSupplierService supplierService;

    @Operation(summary = "查询某物料的供应商报价列表 (可筛选 supplierId/status)")
    @PreAuthorize("@ps.hasPermi('pdm:supplier:list')")
    @GetMapping("/list")
    public R<List<PdmMaterialSupplier>> list(
            @RequestParam Long materialId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Integer status) {
        return R.ok(supplierService.listByMaterial(materialId, supplierId, status));
    }

    @Operation(summary = "新增供应商报价 (单条)")
    @PreAuthorize("@ps.hasPermi('pdm:supplier:edit')")
    @OperLog(title = "物料-供应商报价", businessType = 1)
    @PostMapping
    public R<Integer> add(@RequestBody PdmMaterialSupplier s) {
        return supplierService.save(s) ? R.ok(1) : R.fail("新增失败");
    }

    @Operation(summary = "修改供应商报价")
    @PreAuthorize("@ps.hasPermi('pdm:supplier:edit')")
    @OperLog(title = "物料-供应商报价", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody PdmMaterialSupplier s) {
        return supplierService.updateById(s) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除某条供应商报价")
    @PreAuthorize("@ps.hasPermi('pdm:supplier:edit')")
    @OperLog(title = "物料-供应商报价", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return supplierService.removeById(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "物料页 Tab 提交: 全量覆盖供应商报价 (先删再插)")
    @PreAuthorize("@ps.hasPermi('pdm:supplier:edit')")
    @OperLog(title = "物料-供应商报价", businessType = 2)
    @PutMapping("/replace-all/{materialId}")
    public R<Integer> replaceAll(@PathVariable Long materialId,
                                 @RequestBody(required = false) List<PdmMaterialSupplier> list) {
        return supplierService.replaceAll(materialId, list) ? R.ok(1) : R.fail("保存失败");
    }
}
