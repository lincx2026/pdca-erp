package com.pcba.erp.wms.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.wms.entity.WmsStockOut;
import com.pcba.erp.wms.service.IWmsStockOutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "出库单管理")
@RestController
@RequestMapping("/wms/stock-out")
@RequiredArgsConstructor
public class WmsStockOutController {

    private final IWmsStockOutService stockOutService;

    /* ============== 列表/详情 ============== */

    @Operation(summary = "出库单分页查询")
    @PreAuthorize("@ps.hasPermi('wms:stockout:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String orderNo,
                                 @RequestParam(required = false) Long warehouseId,
                                 @RequestParam(required = false) String outType,
                                 @RequestParam(required = false) Integer status) {
        IPage<WmsStockOut> page = stockOutService.selectStockOutPage(pageNum, pageSize,
                orderNo, warehouseId, outType, status);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "出库单详情（含明细）")
    @PreAuthorize("@ps.hasPermi('wms:stockout:list')")
    @GetMapping("/{id}")
    public R<WmsStockOut> get(@PathVariable Long id) {
        return R.ok(stockOutService.getDetail(id));
    }

    @Operation(summary = "获取下一个出库单号")
    @PreAuthorize("@ps.hasPermi('wms:stockout:add')")
    @GetMapping("/next-no")
    public R<String> nextNo() {
        return R.ok(stockOutService.nextOrderNo());
    }

    /* ============== CRUD ============== */

    @Operation(summary = "新增出库单（主表+明细一次性保存，返回出库单ID）")
    @PreAuthorize("@ps.hasPermi('wms:stockout:add')")
    @OperLog(title = "出库单", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody WmsStockOut stockOut) {
        Long id = stockOutService.saveStockOut(stockOut);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改出库单（主表+明细，仅草稿/驳回可改）")
    @PreAuthorize("@ps.hasPermi('wms:stockout:edit')")
    @OperLog(title = "出库单", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody WmsStockOut stockOut) {
        return stockOutService.updateStockOut(stockOut) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除出库单（仅草稿/驳回可删，级联删除明细）")
    @PreAuthorize("@ps.hasPermi('wms:stockout:remove')")
    @OperLog(title = "出库单", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return stockOutService.removeStockOut(id) ? R.ok(1) : R.fail("删除失败");
    }

    /* ============== 状态机 ============== */

    @Operation(summary = "提交审核 草稿→待审核")
    @PreAuthorize("@ps.hasPermi('wms:stockout:audit')")
    @OperLog(title = "出库单", businessType = 4)
    @PutMapping("/submit/{id}")
    public R<Integer> submit(@PathVariable Long id) {
        return stockOutService.submitAudit(id) ? R.ok(1) : R.fail("提交失败");
    }

    @Operation(summary = "审核通过 待审核→已审核")
    @PreAuthorize("@ps.hasPermi('wms:stockout:audit')")
    @OperLog(title = "出库单", businessType = 4)
    @PutMapping("/audit/{id}")
    public R<Integer> audit(@PathVariable Long id) {
        return stockOutService.auditPass(id) ? R.ok(1) : R.fail("审核失败");
    }

    @Operation(summary = "驳回 待审核→已驳回（需 reason）")
    @PreAuthorize("@ps.hasPermi('wms:stockout:audit')")
    @OperLog(title = "出库单", businessType = 4)
    @PutMapping("/reject/{id}")
    public R<Integer> reject(@PathVariable Long id,
                             @RequestParam(required = false) String reason) {
        return stockOutService.reject(id, reason) ? R.ok(1) : R.fail("驳回失败");
    }

    @Operation(summary = "确认出库 已审核→已出库（扣减库存；采购退货类同步反写PO行退回量）")
    @PreAuthorize("@ps.hasPermi('wms:stockout:confirm')")
    @OperLog(title = "出库单", businessType = 4)
    @PutMapping("/confirm/{id}")
    public R<Integer> confirm(@PathVariable Long id) {
        return stockOutService.confirmStockOut(id) ? R.ok(1) : R.fail("确认出库失败");
    }

    @Operation(summary = "取消出库 已出库→已审核（回滚库存+PO退回量）")
    @PreAuthorize("@ps.hasPermi('wms:stockout:confirm')")
    @OperLog(title = "出库单", businessType = 4)
    @PutMapping("/cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id) {
        return stockOutService.cancelStockOut(id) ? R.ok(1) : R.fail("取消出库失败");
    }
}