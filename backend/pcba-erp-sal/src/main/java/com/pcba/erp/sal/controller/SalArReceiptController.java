package com.pcba.erp.sal.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.sal.entity.SalArReceipt;
import com.pcba.erp.sal.service.ISalArReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "应收账-收款单")
@RestController
@RequestMapping("/sal/ar-receipt")
@RequiredArgsConstructor
public class SalArReceiptController {

    private final ISalArReceiptService service;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('sal:arreceipt:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String receiptNo,
                                 @RequestParam(required = false) Long customerId,
                                 @RequestParam(required = false) String customerName,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) String receiptDate,
                                 @RequestParam(required = false) Long planId,
                                 @RequestParam(required = false) Long receivableId) {
        IPage<SalArReceipt> p = service.selectPage(pageNum, pageSize, receiptNo, customerId, customerName, status, receiptDate, planId, receivableId);
        TableDataInfo t = new TableDataInfo();
        t.setRows(p.getRecords()); t.setTotal(p.getTotal()); t.setCode(200); t.setMsg("查询成功");
        return R.ok(t);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('sal:arreceipt:list')")
    @GetMapping("/{id}")
    public R<SalArReceipt> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('sal:arreceipt:add')")
    @OperLog(title = "SalArReceipt", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody SalArReceipt entity) {
        Long id = service.save(entity); return id == null ? R.fail("新增失败") : R.ok(id);
    }
    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('sal:arreceipt:edit')")
    @OperLog(title = "SalArReceipt", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody SalArReceipt entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }
    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('sal:arreceipt:remove')")
    @OperLog(title = "SalArReceipt", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }
    @Operation(summary = "下拉")
    @PreAuthorize("@ps.hasPermi('sal:arreceipt:list')")
    @GetMapping("/options")
    public R<List<SalArReceipt>> options() { return R.ok(service.options()); }

    // 动作
    @Operation(summary = "审批(0→1)")
    @PreAuthorize("@ps.hasPermi('sal:arreceipt:audit')")
    @OperLog(title = "收款审批", businessType = 2)
    @PutMapping("/audit/{id}")
    public R<Integer> audit(@PathVariable Long id, @RequestParam(required = false) String auditBy) {
        return service.audit(id, auditBy) ? R.ok(1) : R.fail("审批失败");
    }

    @Operation(summary = "确认收款成功(1→2)：回写应收+计划")
    @PreAuthorize("@ps.hasPermi('sal:arreceipt:confirm')")
    @OperLog(title = "确认收款成功", businessType = 2)
    @PutMapping("/confirm/{id}")
    public R<Integer> confirm(@PathVariable Long id, @RequestParam(required = false) String receiptBy) {
        return service.confirm(id, receiptBy) ? R.ok(1) : R.fail("确认收款失败");
    }

    @Operation(summary = "标记收款失败(1→3)")
    @PreAuthorize("@ps.hasPermi('sal:arreceipt:confirm')")
    @OperLog(title = "标记收款失败", businessType = 2)
    @PutMapping("/fail/{id}")
    public R<Integer> fail(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return service.fail(id, reason) ? R.ok(1) : R.fail("标记失败");
    }

    @Operation(summary = "作废收款单/取消(含已收款回滚)")
    @PreAuthorize("@ps.hasPermi('sal:arreceipt:cancel')")
    @OperLog(title = "作废收款单", businessType = 2)
    @PutMapping("/cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return service.cancel(id, reason) ? R.ok(1) : R.fail("取消失败");
    }
}
