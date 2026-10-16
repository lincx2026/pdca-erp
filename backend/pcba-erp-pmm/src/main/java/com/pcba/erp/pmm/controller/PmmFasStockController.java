package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.FasProductStock;
import com.pcba.erp.pmm.service.IPmmFasStockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "成品仓库(完工入库/出库/流水/台账)")
@RestController
@RequestMapping("/fas")
@RequiredArgsConstructor
public class PmmFasStockController {

    private final IPmmFasStockService service;

    /* ======================= 成品库存台账 ======================= */
    @Operation(summary = "成品库存台账分页查询 (仅成品 stock_type=FG)")
    @PreAuthorize("@ps.hasPermi('wh:finished:list')")
    @GetMapping("/stock/list")
    public R<TableDataInfo> stockList(@RequestParam(defaultValue = "1") int pageNum,
                                      @RequestParam(defaultValue = "20") int pageSize,
                                      @RequestParam(required = false) String productName,
                                      @RequestParam(required = false) String batchNo,
                                      @RequestParam(required = false) String warehouse) {
        IPage<FasProductStock> page = service.selectPageByScope(pageNum, pageSize, "FG", productName, batchNo, warehouse);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "成品库存详情")
    @PreAuthorize("@ps.hasPermi('wh:finished:query')")
    @GetMapping("/stock/{id}")
    public R<FasProductStock> stockGet(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "直接出库(保留旧接口)")
    @PreAuthorize("@ps.hasPermi('wh:finished:out')")
    @OperLog(title = "成品库存台账", businessType = 4)
    @PostMapping("/stock/{stockId}/out")
    public R<Void> stockOut(@PathVariable Long stockId, @RequestParam Integer qty) {
        service.stockOut(stockId, qty);
        return R.ok();
    }

    @Operation(summary = "删除台账记录")
    @PreAuthorize("@ps.hasPermi('wh:finished:remove')")
    @OperLog(title = "成品库存台账", businessType = 3)
    @DeleteMapping("/stock/{stockId}")
    public R<Void> stockRemove(@PathVariable Long stockId) {
        service.remove(stockId);
        return R.ok();
    }

    /* ======================= 完工入库单 ======================= */
    @Operation(summary = "完工入库单分页查询")
    @PreAuthorize("@ps.hasPermi('wh:finished:inbound:list')")
    @GetMapping("/finished-inbound/list")
    public R<TableDataInfo> inboundList(@RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "20") int pageSize,
                                        @RequestParam(required = false) Integer docStatus,
                                        @RequestParam(required = false) String docNo,
                                        @RequestParam(required = false) String productName,
                                        @RequestParam(required = false) String batchNo,
                                        @RequestParam(required = false) String warehouse) {
        IPage<FasProductStock> page = service.selectInboundPage(pageNum, pageSize, docStatus, docNo, productName, batchNo, warehouse);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        return R.ok(info);
    }

    @Operation(summary = "完工入库单详情")
    @PreAuthorize("@ps.hasPermi('wh:finished:inbound:query')")
    @GetMapping("/finished-inbound/{id}")
    public R<FasProductStock> inboundGet(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "提交完工入库单审核(草稿→待审核)")
    @PreAuthorize("@ps.hasPermi('wh:finished:inbound:edit')")
    @OperLog(title = "完工入库单", businessType = 2)
    @PostMapping("/finished-inbound/{id}/submit")
    public R<Void> inboundSubmit(@PathVariable Long id) {
        service.submitAudit(id);
        return R.ok();
    }

    @Operation(summary = "完工入库单审核通过")
    @PreAuthorize("@ps.hasPermi('wh:finished:inbound:audit')")
    @OperLog(title = "完工入库单", businessType = 2)
    @PostMapping("/finished-inbound/{id}/audit-pass")
    public R<Void> inboundAuditPass(@PathVariable Long id) {
        service.auditPass(id);
        return R.ok();
    }

    @Operation(summary = "完工入库单驳回")
    @PreAuthorize("@ps.hasPermi('wh:finished:inbound:audit')")
    @OperLog(title = "完工入库单", businessType = 2)
    @PostMapping("/finished-inbound/{id}/reject")
    public R<Void> inboundReject(@PathVariable Long id, @RequestParam String rejectReason) {
        service.auditReject(id, rejectReason);
        return R.ok();
    }

    @Operation(summary = "删除完工入库单(草稿/驳回)")
    @PreAuthorize("@ps.hasPermi('wh:finished:inbound:remove')")
    @OperLog(title = "完工入库单", businessType = 3)
    @DeleteMapping("/finished-inbound/{id}")
    public R<Void> inboundRemove(@PathVariable Long id) {
        service.removeDoc(id);
        return R.ok();
    }

    /* ======================= 成品出库单 ======================= */
    @Operation(summary = "成品出库单分页查询")
    @PreAuthorize("@ps.hasPermi('wh:finished:outbound:list')")
    @GetMapping("/finished-outbound/list")
    public R<TableDataInfo> outboundList(@RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "20") int pageSize,
                                         @RequestParam(required = false) Integer docStatus,
                                         @RequestParam(required = false) String docNo,
                                         @RequestParam(required = false) String productName,
                                         @RequestParam(required = false) String batchNo,
                                         @RequestParam(required = false) String warehouse) {
        IPage<FasProductStock> page = service.selectOutboundPage(pageNum, pageSize, docStatus, docNo, productName, batchNo, warehouse);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        return R.ok(info);
    }

    @Operation(summary = "成品出库单详情")
    @PreAuthorize("@ps.hasPermi('wh:finished:outbound:query')")
    @GetMapping("/finished-outbound/{id}")
    public R<FasProductStock> outboundGet(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "新建成品出库单(草稿)")
    @PreAuthorize("@ps.hasPermi('wh:finished:outbound:add')")
    @OperLog(title = "成品出库单", businessType = 1)
    @PostMapping("/finished-outbound")
    public R<Long> outboundCreate(@RequestBody FasProductStock stock) {
        return R.ok(service.createOutboundDraft(stock));
    }

    @Operation(summary = "提交成品出库单审核")
    @PreAuthorize("@ps.hasPermi('wh:finished:outbound:edit')")
    @OperLog(title = "成品出库单", businessType = 2)
    @PostMapping("/finished-outbound/{id}/submit")
    public R<Void> outboundSubmit(@PathVariable Long id) {
        service.submitAudit(id);
        return R.ok();
    }

    @Operation(summary = "成品出库单审核通过")
    @PreAuthorize("@ps.hasPermi('wh:finished:outbound:audit')")
    @OperLog(title = "成品出库单", businessType = 2)
    @PostMapping("/finished-outbound/{id}/audit-pass")
    public R<Void> outboundAuditPass(@PathVariable Long id) {
        service.outboundAuditPass(id);
        return R.ok();
    }

    @Operation(summary = "成品出库单驳回")
    @PreAuthorize("@ps.hasPermi('wh:finished:outbound:audit')")
    @OperLog(title = "成品出库单", businessType = 2)
    @PostMapping("/finished-outbound/{id}/reject")
    public R<Void> outboundReject(@PathVariable Long id, @RequestParam String rejectReason) {
        service.outboundAuditReject(id, rejectReason);
        return R.ok();
    }

    @Operation(summary = "删除成品出库单")
    @PreAuthorize("@ps.hasPermi('wh:finished:outbound:remove')")
    @OperLog(title = "成品出库单", businessType = 3)
    @DeleteMapping("/finished-outbound/{id}")
    public R<Void> outboundRemove(@PathVariable Long id) {
        service.removeDoc(id);
        return R.ok();
    }

    /* ======================= 出入库流水 ======================= */
    @Operation(summary = "出入库流水分页查询")
    @PreAuthorize("@ps.hasPermi('wh:finished:flow:list')")
    @GetMapping("/finished-flow/list")
    public R<TableDataInfo> flowList(@RequestParam(defaultValue = "1") int pageNum,
                                     @RequestParam(defaultValue = "20") int pageSize,
                                     @RequestParam(required = false) String docType,
                                     @RequestParam(required = false) String sourceType,
                                     @RequestParam(required = false) String productName,
                                     @RequestParam(required = false) String warehouse,
                                     @RequestParam(required = false) String beginTime,
                                     @RequestParam(required = false) String endTime) {
        IPage<FasProductStock> page = service.selectFlowPage(pageNum, pageSize, docType, sourceType, productName, warehouse, beginTime, endTime);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        return R.ok(info);
    }

    /* ======================= 配件仓库 (外购配件:线材/电池/适配器等, 软复用 fas_product_stock stock_type=ACC) ======================= */
    @Operation(summary = "配件库存台账分页查询 (仅外购配件 stock_type=ACC)")
    @PreAuthorize("@ps.hasPermi('wh:acc:list')")
    @GetMapping("/acc-stock/list")
    public R<TableDataInfo> accStockList(@RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "20") int pageSize,
                                         @RequestParam(required = false) String productName,
                                         @RequestParam(required = false) String batchNo,
                                         @RequestParam(required = false) String warehouse) {
        IPage<FasProductStock> page = service.selectPageByScope(pageNum, pageSize, "ACC", productName, batchNo, warehouse);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "配件库存详情")
    @PreAuthorize("@ps.hasPermi('wh:acc:query')")
    @GetMapping("/acc-stock/{id}")
    public R<FasProductStock> accStockGet(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "配件直接出库(保留旧接口)")
    @PreAuthorize("@ps.hasPermi('wh:acc:out')")
    @OperLog(title = "配件库存台账", businessType = 4)
    @PostMapping("/acc-stock/{stockId}/out")
    public R<Void> accStockOut(@PathVariable Long stockId, @RequestParam Integer qty) {
        service.stockOut(stockId, qty);
        return R.ok();
    }

    @Operation(summary = "删除配件台账记录")
    @PreAuthorize("@ps.hasPermi('wh:acc:remove')")
    @OperLog(title = "配件库存台账", businessType = 3)
    @DeleteMapping("/acc-stock/{stockId}")
    public R<Void> accStockRemove(@PathVariable Long stockId) {
        service.remove(stockId);
        return R.ok();
    }
}
