package com.pcba.erp.scm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.scm.entity.ScmApPayable;
import com.pcba.erp.scm.entity.ScmApPayableLine;
import com.pcba.erp.scm.entity.ScmApPaymentPlan;
import com.pcba.erp.scm.service.IScmApPayableLineService;
import com.pcba.erp.scm.service.IScmApPayableService;
import com.pcba.erp.scm.service.IScmApPaymentPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "应付账-应付单")
@RestController
@RequestMapping("/scm/ap-payable")
@RequiredArgsConstructor
public class ScmApPayableController {

    private final IScmApPayableService service;
    private final IScmApPayableLineService lineService;
    private final IScmApPaymentPlanService planService;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('scm:appayable:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String payableNo,
                                 @RequestParam(required = false) Long supplierId,
                                 @RequestParam(required = false) String supplierName,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) String bizDate,
                                 @RequestParam(required = false) String dueDate,
                                 @RequestParam(required = false) Integer isOverdue) {
        IPage<ScmApPayable> p = service.selectPage(pageNum, pageSize, payableNo, supplierId, supplierName, status, bizDate, dueDate, isOverdue);
        TableDataInfo t = new TableDataInfo();
        t.setRows(p.getRecords()); t.setTotal(p.getTotal()); t.setCode(200); t.setMsg("查询成功");
        return R.ok(t);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('scm:appayable:list')")
    @GetMapping("/{id}")
    public R<ScmApPayable> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增(手工应付单)")
    @PreAuthorize("@ps.hasPermi('scm:appayable:add')")
    @OperLog(title = "ScmApPayable", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody ScmApPayable entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('scm:appayable:edit')")
    @OperLog(title = "ScmApPayable", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody ScmApPayable entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('scm:appayable:remove')")
    @OperLog(title = "ScmApPayable", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "下拉")
    @PreAuthorize("@ps.hasPermi('scm:appayable:list')")
    @GetMapping("/options")
    public R<List<ScmApPayable>> options() { return R.ok(service.options()); }

    // 行项
    @Operation(summary = "应付行分页")
    @PreAuthorize("@ps.hasPermi('scm:appayable:list')")
    @GetMapping("/{id}/lines")
    public R<TableDataInfo> lines(@PathVariable Long id,
                                  @RequestParam(defaultValue = "1") int pageNum,
                                  @RequestParam(defaultValue = "100") int pageSize,
                                  @RequestParam(required = false) String materialCode,
                                  @RequestParam(required = false) String materialName) {
        IPage<ScmApPayableLine> p = lineService.selectPage(pageNum, pageSize, id, materialCode, materialName);
        TableDataInfo t = new TableDataInfo();
        t.setRows(p.getRecords()); t.setTotal(p.getTotal()); t.setCode(200); t.setMsg("查询成功");
        return R.ok(t);
    }

    @Operation(summary = "新增应付行")
    @PreAuthorize("@ps.hasPermi('scm:appayable:edit')")
    @OperLog(title = "ScmApPayableLine", businessType = 1)
    @PostMapping("/line")
    public R<Long> addLine(@RequestBody ScmApPayableLine entity) {
        Long id = lineService.save(entity); return id == null ? R.fail("新增失败") : R.ok(id);
    }
    @Operation(summary = "修改应付行")
    @PreAuthorize("@ps.hasPermi('scm:appayable:edit')")
    @OperLog(title = "ScmApPayableLine", businessType = 2)
    @PutMapping("/line")
    public R<Integer> editLine(@RequestBody ScmApPayableLine entity) {
        return lineService.update(entity) ? R.ok(1) : R.fail("修改失败");
    }
    @Operation(summary = "删除应付行")
    @PreAuthorize("@ps.hasPermi('scm:appayable:edit')")
    @OperLog(title = "ScmApPayableLine", businessType = 3)
    @DeleteMapping("/line/{id}")
    public R<Integer> removeLine(@PathVariable Long id) {
        return lineService.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    // 计划
    @Operation(summary = "该应付单的付款计划列表")
    @PreAuthorize("@ps.hasPermi('scm:appayable:list')")
    @GetMapping("/{id}/plans")
    public R<List<ScmApPaymentPlan>> plans(@PathVariable Long id) { return R.ok(planService.listByPayable(id)); }

    @Operation(summary = "生成/重建付款计划(分期)。phases:[{phaseNo,phaseName,ratio,planDate}]，ratio之和需=100")
    @PreAuthorize("@ps.hasPermi('scm:appayable:plan')")
    @OperLog(title = "生成付款计划", businessType = 2)
    @PostMapping("/{id}/plans/generate")
    public R<Integer> generatePlans(@PathVariable Long id, @RequestBody List<Map<String,Object>> phases) {
        return service.generatePlans(id, phases) ? R.ok(1) : R.fail("生成失败");
    }

    // 业务动作
    @Operation(summary = "对账完成(状态→2，推送应付凭证)")
    @PreAuthorize("@ps.hasPermi('scm:appayable:reconcile')")
    @OperLog(title = "应付对账", businessType = 2)
    @PutMapping("/reconcile/{id}")
    public R<Integer> reconcile(@PathVariable Long id, @RequestParam(required = false) String reconcileBy) {
        return service.reconcileManual(id, reconcileBy) ? R.ok(1) : R.fail("对账失败");
    }

    @Operation(summary = "作废应付单")
    @PreAuthorize("@ps.hasPermi('scm:appayable:cancel')")
    @OperLog(title = "作废应付单", businessType = 2)
    @PutMapping("/cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id) {
        return service.cancelPayable(id) ? R.ok(1) : R.fail("作废失败");
    }

    // 看板
    @Operation(summary = "应付看板KPI汇总")
    @PreAuthorize("@ps.hasPermi('scm:apkanban:list')")
    @GetMapping("/kanban/summary")
    public R<Map<String,Object>> kanbanSummary(@RequestParam(required = false) Long supplierId) {
        return R.ok(service.getKanbanSummary(supplierId));
    }
    @Operation(summary = "应付看板供方TOP未付")
    @PreAuthorize("@ps.hasPermi('scm:apkanban:list')")
    @GetMapping("/kanban/top-suppliers")
    public R<List<Map<String,Object>>> topSuppliers(@RequestParam(required = false) Long supplierId,
                                                    @RequestParam(defaultValue = "10") int limit) {
        return R.ok(service.getTopSuppliers(supplierId, limit));
    }
    @Operation(summary = "应付看板账龄分桶")
    @PreAuthorize("@ps.hasPermi('scm:apkanban:list')")
    @GetMapping("/kanban/aging")
    public R<List<Map<String,Object>>> aging(@RequestParam(required = false) Long supplierId) {
        return R.ok(service.getAgingBuckets(supplierId));
    }
}
