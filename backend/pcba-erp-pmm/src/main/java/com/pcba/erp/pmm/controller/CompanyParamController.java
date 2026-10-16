package com.pcba.erp.pmm.controller;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.pmm.entity.CompanyParam;
import com.pcba.erp.pmm.service.IPmmCompanyParamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统参数 Controller
 * 权限前缀: company:param:*
 */
@Tag(name = "系统参数")
@RestController
@RequestMapping("/company/param")
@RequiredArgsConstructor
public class CompanyParamController {

    private final IPmmCompanyParamService paramService;

    @Operation(summary = "系统参数列表")
    @PreAuthorize("@ps.hasPermi('company:param:list')")
    @GetMapping("/list")
    public R<List<CompanyParam>> list() {
        return R.ok(paramService.listAll());
    }

    @Operation(summary = "保存系统参数(新增或更新)")
    @PreAuthorize("@ps.hasPermi('company:param:save')")
    @OperLog(title = "系统参数-保存", businessType = 1)
    @PostMapping
    public R<Void> save(@Validated @RequestBody CompanyParam entity) {
        paramService.save(entity);
        return R.ok();
    }

    @Operation(summary = "删除系统参数")
    @PreAuthorize("@ps.hasPermi('company:param:save')")
    @OperLog(title = "系统参数-删除", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable("id") Long id) {
        paramService.remove(id);
        return R.ok();
    }
}
