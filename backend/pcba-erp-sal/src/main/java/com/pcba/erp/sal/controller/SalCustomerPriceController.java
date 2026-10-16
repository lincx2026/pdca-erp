package com.pcba.erp.sal.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.sal.entity.SalCustomerPrice;
import com.pcba.erp.sal.service.ISalCustomerPriceService;
import com.pcba.erp.sal.vo.SalCustomerPriceExportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客户专属价格表(销售报价) - 对称 scm_supplier_price + 审批流
 * 状态机: 0草稿→1待审→2已审(生效)→3停用; 4驳回
 */
@Tag(name = "销售-报价")
@RestController
@RequestMapping("/sal/price")
@RequiredArgsConstructor
public class SalCustomerPriceController {

    private final ISalCustomerPriceService service;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('sal:price:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) Long customerId,
                                 @RequestParam(required = false) Long materialId,
                                 @RequestParam(required = false) String currency,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) String customerName,
                                 @RequestParam(required = false) Integer onlyValid) {
        IPage<SalCustomerPrice> page = service.selectPage(pageNum, pageSize, customerId, materialId, currency, status, customerName, onlyValid);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('sal:price:list')")
    @GetMapping("/{id}")
    public R<SalCustomerPrice> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('sal:price:add')")
    @OperLog(title = "销售报价", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody SalCustomerPrice entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "批量新增(共享客户/币种/有效期)")
    @PreAuthorize("@ps.hasPermi('sal:price:add')")
    @OperLog(title = "销售报价-批量新增", businessType = 1)
    @PostMapping("/batch")
    public R<List<Long>> batchAdd(@RequestBody List<SalCustomerPrice> items) {
        return R.ok(service.batchSave(items));
    }

    @Operation(summary = "修改(仅草稿/已驳回)")
    @PreAuthorize("@ps.hasPermi('sal:price:edit')")
    @OperLog(title = "销售报价", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody SalCustomerPrice entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除(仅草稿/已驳回)")
    @PreAuthorize("@ps.hasPermi('sal:price:remove')")
    @OperLog(title = "销售报价", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "提交审核(草稿/已驳回→待审)")
    @PreAuthorize("@ps.hasPermi('sal:price:submit')")
    @OperLog(title = "销售报价-提交", businessType = 2)
    @PutMapping("submit/{id}")
    public R<Integer> submit(@PathVariable Long id) { return service.submit(id) ? R.ok(1) : R.fail("提交失败"); }

    @Operation(summary = "审核通过(待审→已审生效)")
    @PreAuthorize("@ps.hasPermi('sal:price:audit')")
    @OperLog(title = "销售报价-审核", businessType = 2)
    @PutMapping("audit/{id}")
    public R<Integer> audit(@PathVariable Long id) { return service.audit(id, currentUser()) ? R.ok(1) : R.fail("审核失败"); }

    @Operation(summary = "驳回(待审→已驳回)")
    @PreAuthorize("@ps.hasPermi('sal:price:reject')")
    @OperLog(title = "销售报价-驳回", businessType = 2)
    @PutMapping("reject/{id}")
    public R<Integer> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = (body != null && body.get("reason") != null) ? String.valueOf(body.get("reason")) : "";
        return service.reject(id, reason, currentUser()) ? R.ok(1) : R.fail("驳回失败");
    }

    @Operation(summary = "停用(已审→停用)")
    @PreAuthorize("@ps.hasPermi('sal:price:edit')")
    @OperLog(title = "销售报价-停用", businessType = 2)
    @PutMapping("disable/{id}")
    public R<Integer> disable(@PathVariable Long id) { return service.disable(id) ? R.ok(1) : R.fail("停用失败"); }

    @Operation(summary = "启用(停用→已审生效)")
    @PreAuthorize("@ps.hasPermi('sal:price:edit')")
    @OperLog(title = "销售报价-启用", businessType = 2)
    @PutMapping("enable/{id}")
    public R<Integer> enable(@PathVariable Long id) { return service.enable(id) ? R.ok(1) : R.fail("启用失败"); }

    @Operation(summary = "设为默认(同客户+物料生效价仅一个默认)")
    @PreAuthorize("@ps.hasPermi('sal:price:default')")
    @OperLog(title = "销售报价-设默认", businessType = 2)
    @PutMapping("setDefault/{id}")
    public R<Integer> setDefault(@PathVariable Long id) { return service.setDefault(id) ? R.ok(1) : R.fail("设置失败"); }

    @Operation(summary = "SO下单带价: 按客户+物料+数量+日期匹配最优生效价")
    @PreAuthorize("@ps.hasPermi('sal:price:list')")
    @GetMapping("/match")
    public R<SalCustomerPrice> match(@RequestParam Long customerId,
                                     @RequestParam Long materialId,
                                     @RequestParam(required = false) BigDecimal qty,
                                     @RequestParam(required = false) LocalDate date) {
        return R.ok(service.matchPrice(customerId, materialId, qty, date));
    }

    @Operation(summary = "下拉选项")
    @PreAuthorize("@ps.hasPermi('sal:price:list')")
    @GetMapping("/options")
    public R<List<SalCustomerPrice>> options() { return R.ok(service.options()); }

    // ============ P1: 报价(价目表)转订单 ============

    @Operation(summary = "批量报价条目转1张销售订单(返回soId)")
    @PreAuthorize("@ps.hasPermi('sal:price:convertOrder')")
    @OperLog(title = "销售报价→转订单", businessType = 4)
    @PostMapping("/convert-order")
    public R<Long> convertOrder(@RequestBody Map<String, Object> req) {
        Object customerIdO = req.get("customerId");
        if (customerIdO == null) return R.fail("customerId 必填");
        Long customerId = Long.valueOf(String.valueOf(customerIdO));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) req.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) req.getOrDefault("extra", new java.util.HashMap<String, Object>());
        return R.ok(service.convertToSalesOrder(customerId, items, extra));
    }

    @Operation(summary = "导出(按当前筛选,最多10000条)")
    @PreAuthorize("@ps.hasPermi('sal:price:export')")
    @OperLog(title = "销售报价", businessType = 5)
    @GetMapping("/export")
    public void export(HttpServletResponse resp,
                       @RequestParam(required = false) Long customerId,
                       @RequestParam(required = false) Long materialId,
                       @RequestParam(required = false) String currency,
                       @RequestParam(required = false) Integer status,
                       @RequestParam(required = false) String customerName,
                       @RequestParam(required = false) Integer onlyValid) throws IOException {
        setExcelResp(resp, "销售报价_" + System.currentTimeMillis() + ".xlsx");
        IPage<SalCustomerPrice> page = service.selectPage(1, 10000, customerId, materialId, currency, status, customerName, onlyValid);
        List<SalCustomerPriceExportVO> rows = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        EasyExcel.write(resp.getOutputStream(), SalCustomerPriceExportVO.class).sheet("销售报价").doWrite(rows);
    }

    private SalCustomerPriceExportVO toVO(SalCustomerPrice p) {
        SalCustomerPriceExportVO v = new SalCustomerPriceExportVO();
        v.setCustomerCode(p.getCustomerCode());
        v.setCustomerName(p.getCustomerName());
        v.setMaterialCode(p.getMaterialCode());
        v.setMaterialName(p.getMaterialName());
        v.setSpecification(p.getSpecification());
        v.setUnit(p.getUnit());
        v.setCurrency(p.getCurrency());
        v.setUnitPrice(p.getUnitPrice());
        v.setTaxRate(p.getTaxRate());
        v.setMinQty(p.getMinQty());
        v.setValidFrom(p.getValidFrom() == null ? "" : p.getValidFrom().toString());
        v.setValidTo(p.getValidTo() == null ? "" : p.getValidTo().toString());
        v.setQuoteSource(p.getQuoteSource());
        v.setIsDefault(p.getIsDefault() != null && p.getIsDefault() == 1 ? "是" : "否");
        v.setStatus(statusText(p.getStatus()));
        v.setAuditBy(p.getAuditBy());
        v.setRejectReason(p.getRejectReason());
        v.setRemark(p.getRemark());
        return v;
    }

    private String statusText(Integer s) {
        if (s == null) return "";
        switch (s) {
            case 0: return "草稿";
            case 1: return "待审核";
            case 2: return "已审核";
            case 3: return "已停用";
            case 4: return "已驳回";
            default: return String.valueOf(s);
        }
    }

    private String currentUser() {
        try {
            // 用 getName() 取用户名(短), 避免 principal.toString() 输出整个 LoginUser 对象串导致字段超长
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) { return ""; }
    }

    private void setExcelResp(HttpServletResponse resp, String filename) throws IOException {
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setCharacterEncoding("utf-8");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        resp.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + encoded);
        resp.setHeader("Access-Control-Expose-Headers", HttpHeaders.CONTENT_DISPOSITION);
    }
}
