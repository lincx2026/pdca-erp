package com.pcba.erp.scm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.scm.entity.ScmApPaymentPlan;
import com.pcba.erp.scm.service.IScmApPaymentPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "应付账-付款计划")
@RestController
@RequestMapping("/scm/ap-payment-plan")
@RequiredArgsConstructor
public class ScmApPaymentPlanController {

    private final IScmApPaymentPlanService service;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('scm:apayplan:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String planNo,
                                 @RequestParam(required = false) Long payableId,
                                 @RequestParam(required = false) Long supplierId,
                                 @RequestParam(required = false) String supplierName,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) String planDate,
                                 @RequestParam(required = false) Integer phaseNo) {
        IPage<ScmApPaymentPlan> p = service.selectPage(pageNum, pageSize, planNo, payableId, supplierId, supplierName, status, planDate, phaseNo);
        TableDataInfo t = new TableDataInfo();
        t.setRows(p.getRecords()); t.setTotal(p.getTotal()); t.setCode(200); t.setMsg("查询成功");
        return R.ok(t);
    }
    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('scm:apayplan:list')")
    @GetMapping("/{id}")
    public R<ScmApPaymentPlan> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }
    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('scm:apayplan:add')")
    @OperLog(title = "ScmApPaymentPlan", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody ScmApPaymentPlan entity) {
        Long id = service.save(entity); return id == null ? R.fail("新增失败") : R.ok(id);
    }
    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('scm:apayplan:edit')")
    @OperLog(title = "ScmApPaymentPlan", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody ScmApPaymentPlan entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }
    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('scm:apayplan:remove')")
    @OperLog(title = "ScmApPaymentPlan", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }
}
