package com.pcba.erp.pdm.controller;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.pdm.entity.PdmMaterialAlternate;
import com.pcba.erp.pdm.service.IPdmMaterialAlternateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "替代料关系")
@RestController
@RequestMapping("/pdm/material-alternate")
@RequiredArgsConstructor
public class PdmMaterialAlternateController {

    private final IPdmMaterialAlternateService altService;

    @Operation(summary = "查询替代关系 (mainId=某主料的替代, altId=X能替代谁, groupId, status)")
    @PreAuthorize("@ps.hasPermi('pdm:alt:list')")
    @GetMapping("/list")
    public R<List<PdmMaterialAlternate>> list(
            @RequestParam(required = false) Long mainMaterialId,
            @RequestParam(required = false) Long altMaterialId,
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) Integer status) {
        return R.ok(altService.listAlternates(mainMaterialId, altMaterialId, groupId, status));
    }

    @Operation(summary = "获取替代组号 (前端点击『新增替代组』自动填充)")
    @PreAuthorize("@ps.hasPermi('pdm:alt:edit')")
    @GetMapping("/next-group-id")
    public R<String> nextGroupId() { return R.ok(altService.nextGroupId()); }

    @Operation(summary = "新增替代料关系")
    @PreAuthorize("@ps.hasPermi('pdm:alt:edit')")
    @OperLog(title = "替代料关系", businessType = 1)
    @PostMapping
    public R<Integer> add(@RequestBody PdmMaterialAlternate a) {
        return altService.save(a) ? R.ok(1) : R.fail("新增失败");
    }

    @Operation(summary = "修改替代料关系 (生效中仅允许改状态=停用)")
    @PreAuthorize("@ps.hasPermi('pdm:alt:edit')")
    @OperLog(title = "替代料关系", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody PdmMaterialAlternate a) {
        return altService.updateById(a) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除替代料关系 (生效中请先停用)")
    @PreAuthorize("@ps.hasPermi('pdm:alt:edit')")
    @OperLog(title = "替代料关系", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return altService.removeById(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "物料页 Tab 提交: 按主料全量覆盖 (先删后插，首行组号有效)")
    @PreAuthorize("@ps.hasPermi('pdm:alt:edit')")
    @OperLog(title = "替代料关系", businessType = 2)
    @PutMapping("/replace-all/{mainMaterialId}")
    public R<Integer> replaceAll(@PathVariable Long mainMaterialId,
                                 @RequestBody(required = false) List<PdmMaterialAlternate> list) {
        return altService.replaceAll(mainMaterialId, list) ? R.ok(1) : R.fail("保存失败");
    }

    /* ================= 状态机 4 个操作 (权限 = pdm:alt:audit) ================= */
    @Operation(summary = "提交审批 (草稿/驳回 → 审批中)")
    @PreAuthorize("@ps.hasPermi('pdm:alt:audit')")
    @OperLog(title = "替代料关系", businessType = 4)
    @PutMapping("/submit-audit/{id}")
    public R<Integer> submitAudit(@PathVariable Long id) {
        return altService.submitAudit(id) ? R.ok(1) : R.fail("提交失败");
    }

    @Operation(summary = "审批通过 (审批中 → 生效)")
    @PreAuthorize("@ps.hasPermi('pdm:alt:audit')")
    @OperLog(title = "替代料关系", businessType = 4)
    @PutMapping("/audit-pass/{id}")
    public R<Integer> auditPass(@PathVariable Long id) {
        String by = SecurityUtils.getUsernameSafe();
        return altService.auditPass(id, by) ? R.ok(1) : R.fail("审批失败");
    }

    @Operation(summary = "审批驳回 (审批中 → 已驳回)")
    @PreAuthorize("@ps.hasPermi('pdm:alt:audit')")
    @OperLog(title = "替代料关系", businessType = 4)
    @PutMapping("/audit-reject/{id}")
    public R<Integer> auditReject(@PathVariable Long id,
                                  @RequestParam(required = false) String rejectReason) {
        String by = SecurityUtils.getUsernameSafe();
        return altService.auditReject(id, by, rejectReason) ? R.ok(1) : R.fail("驳回失败");
    }

    @Operation(summary = "停用 (生效 → 停用)")
    @PreAuthorize("@ps.hasPermi('pdm:alt:edit')")
    @OperLog(title = "替代料关系", businessType = 2)
    @PutMapping("/disable/{id}")
    public R<Integer> disable(@PathVariable Long id) {
        return altService.disable(id) ? R.ok(1) : R.fail("停用失败");
    }
}
