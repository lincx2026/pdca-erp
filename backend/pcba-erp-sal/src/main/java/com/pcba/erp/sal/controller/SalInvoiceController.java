package com.pcba.erp.sal.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.sal.entity.SalInvoice;
import com.pcba.erp.sal.entity.SalInvoiceLine;
import com.pcba.erp.sal.service.ISalInvoiceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 销售开票管理
 * 蓝字开票 + 作废回退 + 部分金额可拆分(一单多票)
 */
@Tag(name = "销售-开票")
@RestController
@RequestMapping("/sal/invoice")
@RequiredArgsConstructor
public class SalInvoiceController {

    private final ISalInvoiceService service;
    private final ObjectMapper objectMapper;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('sal:invoice:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String invoiceNo,
                                 @RequestParam(required = false) Long customerId,
                                 @RequestParam(required = false) String invoiceType,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        IPage<SalInvoice> p = service.selectPage(pageNum, pageSize, invoiceNo, customerId, invoiceType, status, dateFrom, dateTo);
        TableDataInfo t = new TableDataInfo();
        t.setRows(p.getRecords()); t.setTotal(p.getTotal()); t.setCode(200); t.setMsg("查询成功");
        return R.ok(t);
    }

    @Operation(summary = "详情(含明细)")
    @PreAuthorize("@ps.hasPermi('sal:invoice:list')")
    @GetMapping("/{id}")
    public R<Map<String, Object>> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "查询客户可开票应收单(未开票余额>0)")
    @PreAuthorize("@ps.hasPermi('sal:invoice:list')")
    @GetMapping("/invoiceable")
    public R<List<Map<String, Object>>> invoiceable(@RequestParam Long customerId) {
        List<com.pcba.erp.sal.entity.SalArReceivable> list = service.invoiceableReceivables(customerId);
        List<Map<String, Object>> rows = new java.util.ArrayList<>(list.size());
        for (com.pcba.erp.sal.entity.SalArReceivable r : list) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("receivableId", r.getReceivableId());
            m.put("receivableNo", r.getReceivableNo());
            m.put("bizDate", r.getBizDate());
            m.put("bizType", r.getBizType());
            m.put("totalAmount", r.getTotalAmount());
            m.put("invoicedAmount", r.getInvoicedAmount() == null ? java.math.BigDecimal.ZERO : r.getInvoicedAmount());
            m.put("balance", r.getTotalAmount().subtract(r.getInvoicedAmount() == null ? java.math.BigDecimal.ZERO : r.getInvoicedAmount()));
            m.put("currency", r.getCurrency());
            rows.add(m);
        }
        return R.ok(rows);
    }

    @Operation(summary = "新增草稿开票单(含明细)")
    @PreAuthorize("@ps.hasPermi('sal:invoice:add')")
    @OperLog(title = "SalInvoice", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody Map<String, Object> body) {
        SalInvoice inv = objectMapper.convertValue(body.get("invoice"), SalInvoice.class);
        List<SalInvoiceLine> lines = toLines(body.get("lines"));
        Long id = service.save(inv, lines);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "编辑(仅草稿)")
    @PreAuthorize("@ps.hasPermi('sal:invoice:edit')")
    @OperLog(title = "SalInvoice", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody Map<String, Object> body) {
        SalInvoice inv = objectMapper.convertValue(body.get("invoice"), SalInvoice.class);
        List<SalInvoiceLine> lines = toLines(body.get("lines"));
        return service.update(inv, lines) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除(仅草稿)")
    @PreAuthorize("@ps.hasPermi('sal:invoice:remove')")
    @OperLog(title = "SalInvoice", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "确认开票(0→1,回写应收已开票金额)")
    @PreAuthorize("@ps.hasPermi('sal:invoice:issue')")
    @OperLog(title = "确认开票", businessType = 2)
    @PutMapping("/issue/{id}")
    public R<Integer> issue(@PathVariable Long id) {
        return service.issue(id, currentUserName()) ? R.ok(1) : R.fail("开票失败");
    }

    @Operation(summary = "作废(1→2,回退应收已开票金额)")
    @PreAuthorize("@ps.hasPermi('sal:invoice:cancel')")
    @OperLog(title = "作废发票", businessType = 2)
    @PutMapping("/cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id) {
        return service.cancel(id) ? R.ok(1) : R.fail("作废失败");
    }

    private String currentUserName() {
        try {
            return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) { return ""; }
    }

    @SuppressWarnings("unchecked")
    private List<SalInvoiceLine> toLines(Object raw) {
        if (raw == null) return new ArrayList<>();
        List<Object> list = (List<Object>) raw;
        List<SalInvoiceLine> lines = new ArrayList<>(list.size());
        for (Object o : list) {
            lines.add(objectMapper.convertValue(o, SalInvoiceLine.class));
        }
        return lines;
    }
}
