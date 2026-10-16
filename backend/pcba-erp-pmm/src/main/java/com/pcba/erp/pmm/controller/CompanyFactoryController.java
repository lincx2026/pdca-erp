package com.pcba.erp.pmm.controller;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.pmm.entity.CompanyFactory;
import com.pcba.erp.pmm.service.IPmmCompanyFactoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 厂区/产线 Controller (树形结构)
 * 权限前缀: company:factory:*
 */
@Tag(name = "厂区/产线")
@RestController
@RequestMapping("/company/factory")
@RequiredArgsConstructor
public class CompanyFactoryController {

    private final IPmmCompanyFactoryService factoryService;

    @Operation(summary = "厂区/产线树形列表")
    @PreAuthorize("@ps.hasPermi('company:factory:list')")
    @GetMapping("/list")
    public R<List<CompanyFactory>> list() {
        return R.ok(factoryService.listTree());
    }

    @Operation(summary = "厂区/产线详情")
    @PreAuthorize("@ps.hasPermi('company:factory:query')")
    @GetMapping("/{id}")
    public R<CompanyFactory> get(@PathVariable("id") Long id) {
        return R.ok(factoryService.getDetail(id));
    }

    @Operation(summary = "新增厂区/产线")
    @PreAuthorize("@ps.hasPermi('company:factory:add')")
    @OperLog(title = "厂区/产线-新增", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody CompanyFactory entity) {
        return R.ok(factoryService.create(entity));
    }

    @Operation(summary = "编辑厂区/产线")
    @PreAuthorize("@ps.hasPermi('company:factory:edit')")
    @OperLog(title = "厂区/产线-编辑", businessType = 2)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody CompanyFactory entity) {
        factoryService.update(entity);
        return R.ok();
    }

    @Operation(summary = "删除厂区/产线")
    @PreAuthorize("@ps.hasPermi('company:factory:remove')")
    @OperLog(title = "厂区/产线-删除", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable("id") Long id) {
        factoryService.remove(id);
        return R.ok();
    }
}
