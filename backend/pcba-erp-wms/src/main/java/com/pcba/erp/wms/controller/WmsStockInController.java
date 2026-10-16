package com.pcba.erp.wms.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.wms.entity.WmsStockIn;
import com.pcba.erp.wms.service.IWmsStockInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "入库单管理")
@RestController
@RequestMapping("/wms/stock-in")
@RequiredArgsConstructor
public class WmsStockInController {

    private final IWmsStockInService stockInService;

    /* ============== 列表/详情 ============== */

    @Operation(summary = "入库单分页查询")
    @PreAuthorize("@ps.hasPermi('wms:stockin:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String orderNo,
                                 @RequestParam(required = false) Long warehouseId,
                                 @RequestParam(required = false) String inType,
                                 @RequestParam(required = false) Integer status) {
        IPage<WmsStockIn> page = stockInService.selectStockInPage(pageNum, pageSize, orderNo, warehouseId, inType, status);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "入库单详情（含明细）")
    @PreAuthorize("@ps.hasPermi('wms:stockin:list')")
    @GetMapping("/{id}")
    public R<WmsStockIn> get(@PathVariable Long id) {
        return R.ok(stockInService.getDetail(id));
    }

    @Operation(summary = "获取下一个入库单号")
    @PreAuthorize("@ps.hasPermi('wms:stockin:add')")
    @GetMapping("/next-no")
    public R<String> nextNo() {
        return R.ok(stockInService.nextOrderNo());
    }

    /* ============== CRUD ============== */

    @Operation(summary = "新增入库单（主表+明细一次性保存，返回ID）")
    @PreAuthorize("@ps.hasPermi('wms:stockin:add')")
    @OperLog(title = "入库单", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody WmsStockIn stockIn) {
        Long id = stockInService.saveStockIn(stockIn);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改入库单（仅草稿/驳回可改）")
    @PreAuthorize("@ps.hasPermi('wms:stockin:edit')")
    @OperLog(title = "入库单", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody WmsStockIn stockIn) {
        return stockInService.updateStockIn(stockIn) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除入库单（仅草稿/驳回可删，级联删除明细）")
    @PreAuthorize("@ps.hasPermi('wms:stockin:remove')")
    @OperLog(title = "入库单", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return stockInService.removeStockIn(id) ? R.ok(1) : R.fail("删除失败");
    }

    /* ============== 状态机 ============== */

    @Operation(summary = "提交审核 草稿→待审核")
    @PreAuthorize("@ps.hasPermi('wms:stockin:audit')")
    @PutMapping("/submit/{id}")
    public R<Integer> submit(@PathVariable Long id) {
        return stockInService.submitAudit(id) ? R.ok(1) : R.fail("提交失败");
    }

    @Operation(summary = "审核通过 待审核→已审核")
    @PreAuthorize("@ps.hasPermi('wms:stockin:audit')")
    @PutMapping("/audit/{id}")
    public R<Integer> audit(@PathVariable Long id) {
        String by = SecurityUtils.getUsernameSafe();
        return stockInService.auditPass(id, by) ? R.ok(1) : R.fail("审核失败");
    }

    @Operation(summary = "驳回 待审核→已驳回")
    @PreAuthorize("@ps.hasPermi('wms:stockin:audit')")
    @PutMapping("/reject/{id}")
    public R<Integer> reject(@PathVariable Long id,
                             @RequestParam(required = false) String reason) {
        String by = SecurityUtils.getUsernameSafe();
        return stockInService.reject(id, by, reason) ? R.ok(1) : R.fail("驳回失败");
    }

    @Operation(summary = "确认入库 已审核→已入库（同步增加库存 + 采购类反写PO收货量/状态）")
    @PreAuthorize("@ps.hasPermi('wms:stockin:confirm')")
    @PutMapping("/confirm/{id}")
    public R<Integer> confirm(@PathVariable Long id) {
        return stockInService.confirmStockIn(id) ? R.ok(1) : R.fail("入库失败");
    }

    @Operation(summary = "取消入库 已入库→已审核（回滚库存 + 反写PO收货量/状态回退）")
    @PreAuthorize("@ps.hasPermi('wms:stockin:confirm')")
    @PutMapping("/cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id) {
        return stockInService.cancelStockIn(id) ? R.ok(1) : R.fail("取消入库失败");
    }
}