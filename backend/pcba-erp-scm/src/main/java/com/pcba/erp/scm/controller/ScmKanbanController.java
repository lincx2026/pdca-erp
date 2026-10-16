package com.pcba.erp.scm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.core.R;
import com.pcba.erp.scm.entity.ScmPurchaseOrder;
import com.pcba.erp.scm.entity.ScmPurchaseRequisition;
import com.pcba.erp.scm.entity.ScmSupplier;
import com.pcba.erp.scm.entity.ScmDeliveryNote;
import com.pcba.erp.scm.entity.ScmPurchaseReturn;
import com.pcba.erp.scm.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@Tag(name = "采购-kanban")
@RestController
@RequestMapping("/scm/kanban")
@RequiredArgsConstructor
public class ScmKanbanController {

    private final IScmPurchaseRequisitionService requisitionService;
    private final IScmPurchaseOrderService orderService;
    private final IScmDeliveryNoteService deliveryService;
    private final IScmPurchaseReturnService returnService;
    private final IScmSupplierService supplierService;
    private final IScmSupplierPriceService priceService;

    @Operation(summary = "采购看板汇总")
    @PreAuthorize("@ps.hasPermi('scm:kanban:list')")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        Map<String, Object> res = new LinkedHashMap<>();

        // 1) 指标卡片
        IPage<ScmPurchaseRequisition> reqPage = requisitionService.selectPage(1, 1000, null, null, null, null, null, null);
        IPage<ScmPurchaseOrder> poPage = orderService.selectPage(1, 1000, null, null, null, null, null, null, null, null);
        IPage<ScmDeliveryNote> dnPage = deliveryService.selectPage(1, 1000, null, null, null, null, null, null, null);
        IPage<ScmPurchaseReturn> rtPage = returnService.selectPage(1, 1000, null, null, null, null, null, null, null);
        long supCount = 0;
        try { supCount = supplierService.selectPage(1, 1, null, null, null, null, null, null).getTotal(); } catch (Exception ignore) {}

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("requisitionCount", reqPage.getTotal());
        metrics.put("orderCount",       poPage.getTotal());
        metrics.put("deliveryCount",    dnPage.getTotal());
        metrics.put("returnCount",      rtPage.getTotal());
        metrics.put("supplierCount",    supCount);

        BigDecimal poAmount = BigDecimal.ZERO;
        for (ScmPurchaseOrder o : safe(poPage.getRecords())) {
            poAmount = poAmount.add(nvl(o.getTotalAmount()));
        }
        metrics.put("orderTotalAmount", poAmount);

        BigDecimal dnAmount = BigDecimal.ZERO;
        for (ScmDeliveryNote d : safe(dnPage.getRecords())) {
            dnAmount = dnAmount.add(nvl(d.getTotalAmount()));
        }
        metrics.put("deliveryTotalAmount", dnAmount);

        res.put("metrics", metrics);

        // 2) 各单据状态计数
        res.put("requisitionStatusCount", statusCount(safe(reqPage.getRecords()), ScmPurchaseRequisition::getStatus));
        res.put("orderStatusCount",       statusCount(safe(poPage.getRecords()),  ScmPurchaseOrder::getStatus));
        res.put("deliveryStatusCount",    statusCount(safe(dnPage.getRecords()),  ScmDeliveryNote::getStatus));
        res.put("returnStatusCount",      statusCount(safe(rtPage.getRecords()),  ScmPurchaseReturn::getStatus));

        // 3) 待办 Top
        List<Map<String, Object>> pendingRequisitions = new ArrayList<>();
        for (ScmPurchaseRequisition r : safe(reqPage.getRecords())) {
            Integer s = r.getStatus();
            if (s == null || s == 0 || s == 1) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.getRequisitionId());
                m.put("no",   r.getRequisitionNo());
                m.put("title",r.getTitle());
                m.put("status", s);
                m.put("date",  Objects.toString(r.getRequisitionDate(), ""));
                m.put("applicant", r.getApplicantName());
                pendingRequisitions.add(m);
                if (pendingRequisitions.size() >= 8) break;
            }
        }
        res.put("pendingRequisitions", pendingRequisitions);

        List<Map<String, Object>> pendingOrders = new ArrayList<>();
        for (ScmPurchaseOrder o : safe(poPage.getRecords())) {
            Integer s = o.getStatus();
            if (s == null || s == 0 || s == 1 || s == 2) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", o.getPoId());
                m.put("no",   o.getPoNo());
                m.put("supplierName", o.getSupplierName());
                m.put("status", s);
                m.put("amount", nvl(o.getTotalAmount()));
                m.put("deliveryDate", Objects.toString(o.getDeliveryDate(), ""));
                pendingOrders.add(m);
                if (pendingOrders.size() >= 8) break;
            }
        }
        res.put("pendingOrders", pendingOrders);

        // 4) Top 供应商（按采购订单金额汇总）
        Map<String, BigDecimal> supMap = new LinkedHashMap<>();
        Map<String, Long> supCnt = new LinkedHashMap<>();
        for (ScmPurchaseOrder o : safe(poPage.getRecords())) {
            String k = o.getSupplierName() == null ? "(未填)" : o.getSupplierName();
            supMap.merge(k, nvl(o.getTotalAmount()), BigDecimal::add);
            supCnt.merge(k, 1L, Long::sum);
        }
        List<Map<String, Object>> topSuppliers = new ArrayList<>();
        supMap.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder())).limit(6)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("amount", e.getValue());
                    m.put("orderCount", supCnt.get(e.getKey()));
                    topSuppliers.add(m);
                });
        res.put("topSuppliers", topSuppliers);

        return R.ok(res);
    }

    // ========= helpers =========
    private static <T> List<T> safe(List<T> list) { return list == null ? Collections.emptyList() : list; }
    private static BigDecimal nvl(BigDecimal b)   { return b == null ? BigDecimal.ZERO : b; }

    @FunctionalInterface
    private interface StatusFn<T> { Integer apply(T t); }

    private static <T> Map<String, Long> statusCount(List<T> list, StatusFn<T> fn) {
        Map<Integer, Long> cnt = new LinkedHashMap<>();
        for (T t : list) {
            Integer s = fn.apply(t);
            cnt.merge(s == null ? -1 : s, 1L, Long::sum);
        }
        Map<String, Long> ordered = new LinkedHashMap<>();
        cnt.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> ordered.put(String.valueOf(e.getKey()), e.getValue()));
        return ordered;
    }
}
