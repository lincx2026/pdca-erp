package com.pcba.erp.pmm.controller;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.pmm.entity.CompanyCodingRule;
import com.pcba.erp.pmm.service.IPmmCompanyCodingRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 编码规则 Controller
 * 权限前缀: company:coding:*
 */
@Tag(name = "编码规则")
@RestController
@RequestMapping("/company/coding")
@RequiredArgsConstructor
public class CompanyCodingRuleController {

    private final IPmmCompanyCodingRuleService codingRuleService;

    @Operation(summary = "编码规则列表")
    @PreAuthorize("@ps.hasPermi('company:coding:list')")
    @GetMapping("/list")
    public R<List<CompanyCodingRule>> list() {
        return R.ok(codingRuleService.listAll());
    }

    @Operation(summary = "保存编码规则(新增或更新)")
    @PreAuthorize("@ps.hasPermi('company:coding:save')")
    @OperLog(title = "编码规则-保存", businessType = 1)
    @PostMapping
    public R<Void> save(@Validated @RequestBody CompanyCodingRule entity) {
        codingRuleService.save(entity);
        return R.ok();
    }

    @Operation(summary = "删除编码规则")
    @PreAuthorize("@ps.hasPermi('company:coding:save')")
    @OperLog(title = "编码规则-删除", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable("id") Long id) {
        codingRuleService.remove(id);
        return R.ok();
    }

    @Operation(summary = "根据模块名生成编码")
    @PreAuthorize("@ps.hasPermi('company:coding:list')")
    @GetMapping("/generate/{moduleName}")
    public R<String> generate(@PathVariable("moduleName") String moduleName) {
        return R.ok(codingRuleService.generateCode(moduleName));
    }
}
