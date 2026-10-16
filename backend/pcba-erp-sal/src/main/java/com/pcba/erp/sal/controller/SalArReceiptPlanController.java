package com.pcba.erp.sal.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.sal.entity.SalArReceiptPlan;
import com.pcba.erp.sal.service.ISalArReceiptPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "应收账-收款计划")
@RestController
@RequestMapping("/sal/ar-receipt-plan")
@RequiredArgsConstructor
public class SalArReceiptPlanController {

    private final ISalArReceiptPlanService service;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('sal:arreceiptplan:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String planNo,
                                 @RequestParam(required = false) Long receivableId,
                                 @RequestParam(required = false) Long customerId,
                                 @RequestParam(required = false) String customerName,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) String planDate,
                                 @RequestParam(required = false) Integer phaseNo) {
        IPage<SalArReceiptPlan> page = service.selectPage(pageNum, pageSize, planNo, receivableId, customerId, customerName, status, planDate, phaseNo);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('sal:arreceiptplan:list')")
    @GetMapping("/{id}")
    public R<SalArReceiptPlan> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('sal:arreceiptplan:add')")
    @OperLog(title = "SalArReceiptPlan", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody SalArReceiptPlan entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('sal:arreceiptplan:edit')")
    @OperLog(title = "SalArReceiptPlan", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody SalArReceiptPlan entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('sal:arreceiptplan:remove')")
    @OperLog(title = "SalArReceiptPlan", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }
}
