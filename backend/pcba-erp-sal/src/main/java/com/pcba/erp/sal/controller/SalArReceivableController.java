package com.pcba.erp.sal.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.sal.entity.SalArReceivable;
import com.pcba.erp.sal.entity.SalArReceivableLine;
import com.pcba.erp.sal.entity.SalArReceiptPlan;
import com.pcba.erp.sal.service.ISalArReceivableLineService;
import com.pcba.erp.sal.service.ISalArReceivableService;
import com.pcba.erp.sal.service.ISalArReceiptPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "应收账-应收单")
@RestController
@RequestMapping("/sal/ar-receivable")
@RequiredArgsConstructor
public class SalArReceivableController {

    private final ISalArReceivableService service;
    private final ISalArReceivableLineService lineService;
    private final ISalArReceiptPlanService planService;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String receivableNo,
                                 @RequestParam(required = false) Long customerId,
                                 @RequestParam(required = false) String customerName,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) String bizDate,
                                 @RequestParam(required = false) String dueDate,
                                 @RequestParam(required = false) Integer isOverdue) {
        IPage<SalArReceivable> p = service.selectPage(pageNum, pageSize, receivableNo, customerId, customerName, status, bizDate, dueDate, isOverdue);
        TableDataInfo t = new TableDataInfo();
        t.setRows(p.getRecords()); t.setTotal(p.getTotal()); t.setCode(200); t.setMsg("查询成功");
        return R.ok(t);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:list')")
    @GetMapping("/{id}")
    public R<SalArReceivable> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增(手工应收单)")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:add')")
    @OperLog(title = "SalArReceivable", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody SalArReceivable entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:edit')")
    @OperLog(title = "SalArReceivable", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody SalArReceivable entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:remove')")
    @OperLog(title = "SalArReceivable", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "下拉")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:list')")
    @GetMapping("/options")
    public R<List<SalArReceivable>> options() { return R.ok(service.options()); }

    // 行项
    @Operation(summary = "应收行分页")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:list')")
    @GetMapping("/{id}/lines")
    public R<TableDataInfo> lines(@PathVariable Long id,
                                  @RequestParam(defaultValue = "1") int pageNum,
                                  @RequestParam(defaultValue = "100") int pageSize,
                                  @RequestParam(required = false) String materialCode,
                                  @RequestParam(required = false) String materialName) {
        IPage<SalArReceivableLine> p = lineService.selectPage(pageNum, pageSize, id, materialCode, materialName);
        TableDataInfo t = new TableDataInfo();
        t.setRows(p.getRecords()); t.setTotal(p.getTotal()); t.setCode(200); t.setMsg("查询成功");
        return R.ok(t);
    }

    // 计划
    @Operation(summary = "该应收单的收款计划列表")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:list')")
    @GetMapping("/{id}/plans")
    public R<List<SalArReceiptPlan>> plans(@PathVariable Long id) { return R.ok(planService.listByReceivable(id)); }

    @Operation(summary = "生成/重建收款计划(分期)。phases:[{phaseNo,phaseName,ratio,planDate}]，ratio之和需=100")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:plan')")
    @OperLog(title = "生成收款计划", businessType = 2)
    @PostMapping("/{id}/plans/generate")
    public R<Integer> generatePlans(@PathVariable Long id, @RequestBody List<Map<String,Object>> phases) {
        return service.generatePlans(id, phases) ? R.ok(1) : R.fail("生成失败");
    }

    // 业务动作 — 对账两级流程 + 反审 + 批量
    @Operation(summary = "提交对账(0待对账→1对账中)；等待客户签字回签")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:reconcile')")
    @OperLog(title = "提交对账", businessType = 2)
    @PutMapping("/submit-reconcile/{id}")
    public R<Integer> submitReconcile(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String,Object> body) {
        String operator = currentUserName();
        String signBy = body == null ? null : (String) body.get("signBy");
        String attachment = body == null ? null : (String) body.get("attachment");
        String remark = body == null ? null : (String) body.get("remark");
        return service.submitReconcile(id, signBy, attachment, remark, operator) ? R.ok(1) : R.fail("提交对账失败");
    }

    @Operation(summary = "确认对账(1对账中→2对账完成)；录入客户签字人/签字时间/财务凭证号")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:reconcile')")
    @OperLog(title = "确认对账", businessType = 2)
    @PutMapping("/confirm-reconcile/{id}")
    public R<Integer> confirmReconcile(@PathVariable Long id, @RequestBody java.util.Map<String,Object> body) {
        String operator = currentUserName();
        String signBy = body == null ? null : (String) body.get("signBy");
        Object st = body == null ? null : body.get("signTime");
        java.time.LocalDateTime signTime = parseLdt(st);
        Long voucherId = parseLong(body == null ? null : body.get("voucherId"));
        return service.confirmReconcile(id, signBy, signTime, voucherId, operator) ? R.ok(1) : R.fail("确认对账失败");
    }

    @Operation(summary = "撤销对账(2对账完成→0待对账)；约束 receivedAmount=0")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:reconcile')")
    @OperLog(title = "撤销对账", businessType = 2)
    @PutMapping("/undo-reconcile/{id}")
    public R<Integer> undoReconcile(@PathVariable Long id) {
        return service.undoReconcile(id) ? R.ok(1) : R.fail("撤销对账失败");
    }

    @Operation(summary = "批量对账(按客户+业务日期范围，0→2)")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:reconcile')")
    @OperLog(title = "批量对账", businessType = 2)
    @PostMapping("/batch-reconcile")
    public R<Integer> batchReconcile(@RequestBody java.util.Map<String,Object> body) {
        if (body == null) return R.fail("参数为空");
        Long customerId = parseLong(body.get("customerId"));
        String dateFrom = body.get("dateFrom") == null ? null : body.get("dateFrom").toString();
        String dateTo = body.get("dateTo") == null ? null : body.get("dateTo").toString();
        int n = service.batchReconcile(customerId, dateFrom, dateTo, currentUserName());
        return R.ok(n);
    }

    @Operation(summary = "作废应收单")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:cancel')")
    @OperLog(title = "作废应收单", businessType = 2)
    @PutMapping("/cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id) {
        return service.cancel(id) ? R.ok(1) : R.fail("作废失败");
    }

    // --- 工具方法 ---
    private String currentUserName() {
        try {
            // 用 getName() 取用户名(短), 避免 principal.toString() 输出整个 LoginUser 对象串导致字段超长
            return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) { return ""; }
    }
    private static java.time.LocalDateTime parseLdt(Object o) {
        if (o == null) return null;
        if (o instanceof java.time.LocalDateTime) return (java.time.LocalDateTime) o;
        String s = o.toString();
        if (s.isBlank()) return null;
        try { return java.time.LocalDateTime.parse(s.replace(' ', 'T')); }
        catch (Exception e) { try { return java.time.LocalDate.parse(s).atStartOfDay(); } catch (Exception ignore) { return null; } }
    }
    private static Long parseLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return null; }
    }

    // 看板
    @Operation(summary = "应收看板KPI汇总")
    @PreAuthorize("@ps.hasPermi('sal:arkanban:list')")
    @GetMapping("/kanban/summary")
    public R<Map<String,Object>> kanbanSummary(@RequestParam(required = false) Long customerId) {
        return R.ok(service.kanbanSummary(customerId));
    }
    @Operation(summary = "应收看板客户TOP未收")
    @PreAuthorize("@ps.hasPermi('sal:arkanban:list')")
    @GetMapping("/kanban/top-customers")
    public R<List<Map<String,Object>>> topCustomers(@RequestParam(required = false) Long customerId,
                                                    @RequestParam(defaultValue = "10") int limit) {
        return R.ok(service.topCustomers(customerId, limit));
    }
    @Operation(summary = "应收看板账龄分桶")
    @PreAuthorize("@ps.hasPermi('sal:arkanban:list')")
    @GetMapping("/kanban/aging")
    public R<List<Map<String,Object>>> aging(@RequestParam(required = false) Long customerId) {
        return R.ok(service.aging(customerId));
    }
}
