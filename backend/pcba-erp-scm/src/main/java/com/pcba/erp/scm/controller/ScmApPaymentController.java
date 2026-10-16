package com.pcba.erp.scm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.scm.entity.ScmApPayment;
import com.pcba.erp.scm.service.IScmApPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "应付账-付款单")
@RestController
@RequestMapping("/scm/ap-payment")
@RequiredArgsConstructor
public class ScmApPaymentController {

    private final IScmApPaymentService service;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('scm:apayment:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String paymentNo,
                                 @RequestParam(required = false) Long supplierId,
                                 @RequestParam(required = false) String supplierName,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) String payDate,
                                 @RequestParam(required = false) Long planId,
                                 @RequestParam(required = false) Long payableId) {
        IPage<ScmApPayment> p = service.selectPage(pageNum, pageSize, paymentNo, supplierId, supplierName, status, payDate, planId, payableId);
        TableDataInfo t = new TableDataInfo();
        t.setRows(p.getRecords()); t.setTotal(p.getTotal()); t.setCode(200); t.setMsg("查询成功");
        return R.ok(t);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('scm:apayment:list')")
    @GetMapping("/{id}")
    public R<ScmApPayment> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('scm:apayment:add')")
    @OperLog(title = "ScmApPayment", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody ScmApPayment entity) {
        Long id = service.save(entity); return id == null ? R.fail("新增失败") : R.ok(id);
    }
    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('scm:apayment:edit')")
    @OperLog(title = "ScmApPayment", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody ScmApPayment entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }
    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('scm:apayment:remove')")
    @OperLog(title = "ScmApPayment", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }
    @Operation(summary = "下拉")
    @PreAuthorize("@ps.hasPermi('scm:apayment:list')")
    @GetMapping("/options")
    public R<List<ScmApPayment>> options() { return R.ok(service.options()); }

    // 动作
    @Operation(summary = "审批(0→1)")
    @PreAuthorize("@ps.hasPermi('scm:apayment:audit')")
    @OperLog(title = "付款审批", businessType = 2)
    @PutMapping("/audit/{id}")
    public R<Integer> audit(@PathVariable Long id, @RequestParam(required = false) String auditBy) {
        return service.audit(id, auditBy) ? R.ok(1) : R.fail("审批失败");
    }

    @Operation(summary = "确认付款成功(1→2)：回写应付+计划+推送付款凭证")
    @PreAuthorize("@ps.hasPermi('scm:apayment:confirm')")
    @OperLog(title = "确认付款成功", businessType = 2)
    @PutMapping("/confirm/{id}")
    public R<Integer> confirm(@PathVariable Long id, @RequestParam(required = false) String payBy) {
        return service.confirmPayment(id, payBy) ? R.ok(1) : R.fail("确认付款失败");
    }

    @Operation(summary = "标记付款失败(1→3)")
    @PreAuthorize("@ps.hasPermi('scm:apayment:confirm')")
    @OperLog(title = "标记付款失败", businessType = 2)
    @PutMapping("/fail/{id}")
    public R<Integer> fail(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return service.markFailed(id, reason) ? R.ok(1) : R.fail("标记失败");
    }

    @Operation(summary = "作废付款单/取消(含已付款回滚)")
    @PreAuthorize("@ps.hasPermi('scm:apayment:cancel')")
    @OperLog(title = "作废付款单", businessType = 2)
    @PutMapping("/cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return service.cancelPayment(id, reason) ? R.ok(1) : R.fail("取消失败");
    }
}
