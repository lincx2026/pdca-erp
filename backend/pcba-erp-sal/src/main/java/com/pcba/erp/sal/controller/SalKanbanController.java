package com.pcba.erp.sal.controller;

import com.pcba.erp.common.core.R;
import com.pcba.erp.sal.service.ISalArReceivableService;
import com.pcba.erp.sal.service.SalKanbanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "销售-kanban")
@RestController
@RequestMapping("/sal/kanban")
@RequiredArgsConstructor
public class SalKanbanController {

    private final ISalArReceivableService receivableService;
    private final SalKanbanService salKanbanService;

    @Operation(summary = "应收看板KPI汇总")
    @PreAuthorize("@ps.hasPermi('sal:arkanban:list')")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary(@RequestParam(required = false) Long customerId) {
        return R.ok(receivableService.kanbanSummary(customerId));
    }

    @Operation(summary = "应收看板客户TOP未收")
    @PreAuthorize("@ps.hasPermi('sal:arkanban:list')")
    @GetMapping("/top-customers")
    public R<List<Map<String, Object>>> topCustomers(@RequestParam(required = false) Long customerId,
                                                     @RequestParam(defaultValue = "10") int limit) {
        return R.ok(receivableService.topCustomers(customerId, limit));
    }

    @Operation(summary = "应收看板账龄分桶")
    @PreAuthorize("@ps.hasPermi('sal:arkanban:list')")
    @GetMapping("/aging")
    public R<List<Map<String, Object>>> aging(@RequestParam(required = false) Long customerId) {
        return R.ok(receivableService.aging(customerId));
    }

    /* ============ 销售业务 KPI（按期交付率/退货率/销售金额） ============ */
    @Operation(summary = "销售业务KPI汇总（交付率/退货率/销售金额）")
    @PreAuthorize("@ps.hasPermi('sal:kanban:list')")
    @GetMapping("/sales-summary")
    public R<Map<String, Object>> salesSummary(@RequestParam(required = false) Long customerId) {
        return R.ok(salKanbanService.salesSummary(customerId));
    }

    @Operation(summary = "客户TOP收款（按已收金额降序）")
    @PreAuthorize("@ps.hasPermi('sal:kanban:list')")
    @GetMapping("/top-received")
    public R<List<Map<String, Object>>> topReceived(@RequestParam(required = false) Long customerId,
                                                    @RequestParam(defaultValue = "10") int limit) {
        return R.ok(salKanbanService.topReceivedCustomers(customerId, limit));
    }

    @Operation(summary = "近N 月销售/退货/收款金额趋势")
    @PreAuthorize("@ps.hasPermi('sal:kanban:list')")
    @GetMapping("/sales-trend")
    public R<List<Map<String, Object>>> salesTrend(@RequestParam(required = false) Long customerId,
                                                  @RequestParam(defaultValue = "6") int months) {
        return R.ok(salKanbanService.salesTrend(customerId, months));
    }
}
