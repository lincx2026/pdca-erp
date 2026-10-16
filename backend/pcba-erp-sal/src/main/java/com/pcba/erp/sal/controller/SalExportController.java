package com.pcba.erp.sal.controller;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.sal.service.SalExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 销售模块 EasyExcel 导出（销售订单 / 销售退货 / 应收单）
 * 与 PDM 导出约定一致：@GetMapping("/export") + @PreAuthorize 权限码 + setExcelResp 头
 */
@Tag(name = "销售-导出")
@RestController
@RequestMapping("/sal/export")
@RequiredArgsConstructor
public class SalExportController {

    private final SalExportService exportService;

    @Operation(summary = "导出销售订单（按当前筛选条件，最多10000条）")
    @PreAuthorize("@ps.hasPermi('sal:order:export')")
    @OperLog(title = "销售订单", businessType = 5)
    @GetMapping("/orders")
    public void exportOrders(HttpServletResponse resp,
                             @RequestParam(required = false) String soNo,
                             @RequestParam(required = false) String soType,
                             @RequestParam(required = false) Long customerId,
                             @RequestParam(required = false) String customerName,
                             @RequestParam(required = false) Long salesRepId,
                             @RequestParam(required = false) Integer status,
                             @RequestParam(required = false) String deliveryDate) throws IOException {
        setExcelResp(resp, "销售订单_" + System.currentTimeMillis() + ".xlsx");
        exportService.exportOrders(resp.getOutputStream(), soNo, soType, customerId,
                customerName, salesRepId, status, deliveryDate);
    }

    @Operation(summary = "导出销售退货（按当前筛选条件，最多10000条）")
    @PreAuthorize("@ps.hasPermi('sal:return:export')")
    @OperLog(title = "销售退货", businessType = 5)
    @GetMapping("/returns")
    public void exportReturns(HttpServletResponse resp,
                              @RequestParam(required = false) String returnNo,
                              @RequestParam(required = false) Long customerId,
                              @RequestParam(required = false) String customerName,
                              @RequestParam(required = false) Long soId,
                              @RequestParam(required = false) Integer status,
                              @RequestParam(required = false) String returnDate) throws IOException {
        setExcelResp(resp, "销售退货_" + System.currentTimeMillis() + ".xlsx");
        exportService.exportReturns(resp.getOutputStream(), returnNo, customerId,
                customerName, soId, status, returnDate);
    }

    @Operation(summary = "导出应收单（按当前筛选条件，最多10000条）")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:export')")
    @OperLog(title = "应收单", businessType = 5)
    @GetMapping("/receivables")
    public void exportReceivables(HttpServletResponse resp,
                                  @RequestParam(required = false) String receivableNo,
                                  @RequestParam(required = false) Long customerId,
                                  @RequestParam(required = false) String customerName,
                                  @RequestParam(required = false) Integer status,
                                  @RequestParam(required = false) String bizDate,
                                  @RequestParam(required = false) String dueDate,
                                  @RequestParam(required = false) Integer isOverdue) throws IOException {
        setExcelResp(resp, "应收单_" + System.currentTimeMillis() + ".xlsx");
        exportService.exportReceivables(resp.getOutputStream(), receivableNo, customerId,
                customerName, status, bizDate, dueDate, isOverdue);
    }

    @Operation(summary = "导出客户对账单(按客户+周期：期初/本期发生/本期收款/期末/明细)")
    @PreAuthorize("@ps.hasPermi('sal:arreceivable:export')")
    @OperLog(title = "客户对账单", businessType = 5)
    @GetMapping("/ar-statement")
    public void exportArStatement(HttpServletResponse resp,
                                  @RequestParam Long customerId,
                                  @RequestParam String dateFrom,
                                  @RequestParam String dateTo) throws IOException {
        setExcelResp(resp, "客户对账单_" + customerId + "_" + dateFrom + "_" + dateTo + ".xlsx");
        exportService.exportArStatement(resp.getOutputStream(), customerId, dateFrom, dateTo);
    }

    @Operation(summary = "导出开票单(按当前筛选条件，最多10000条)")
    @PreAuthorize("@ps.hasPermi('sal:invoice:export')")
    @OperLog(title = "开票单", businessType = 5)
    @GetMapping("/invoices")
    public void exportInvoices(HttpServletResponse resp,
                              @RequestParam(required = false) String invoiceNo,
                              @RequestParam(required = false) Long customerId,
                              @RequestParam(required = false) String invoiceType,
                              @RequestParam(required = false) Integer status,
                              @RequestParam(required = false) String dateFrom,
                              @RequestParam(required = false) String dateTo) throws IOException {
        setExcelResp(resp, "开票单_" + System.currentTimeMillis() + ".xlsx");
        exportService.exportInvoices(resp.getOutputStream(), invoiceNo, customerId, invoiceType, status, dateFrom, dateTo);
    }

    private void setExcelResp(HttpServletResponse resp, String filename) {
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setCharacterEncoding("utf-8");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        resp.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + encoded);
        resp.setHeader("Access-Control-Expose-Headers", HttpHeaders.CONTENT_DISPOSITION);
    }
}
