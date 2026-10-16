package com.pcba.erp.scm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.scm.entity.ScmPurchaseOrder;
import com.pcba.erp.scm.entity.ScmPurchaseOrderLine;
import com.pcba.erp.scm.service.IScmPurchaseOrderLineService;
import com.pcba.erp.scm.service.IScmPurchaseOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "采购-purchase")
@RestController
@RequestMapping("/scm/purchase")
@RequiredArgsConstructor
public class ScmPurchaseOrderController {

    private final IScmPurchaseOrderService service;
    private final IScmPurchaseOrderLineService lineService;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('scm:purchase:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String poNo, @RequestParam(required = false) String poType, @RequestParam(required = false) String bizCategory, @RequestParam(required = false) Long supplierId, @RequestParam(required = false) String supplierName, @RequestParam(required = false) Long buyerId, @RequestParam(required = false) Integer status, @RequestParam(required = false) String deliveryDate) {
        IPage<ScmPurchaseOrder> page = service.selectPage(pageNum, pageSize, poNo, poType, bizCategory, supplierId, supplierName, buyerId, status, deliveryDate);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords()); info.setTotal(page.getTotal()); info.setCode(200); info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('scm:purchase:list')")
    @GetMapping("/{id}")
    public R<ScmPurchaseOrder> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('scm:purchase:add')")
    @OperLog(title = "ScmPurchaseOrder", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody ScmPurchaseOrder entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('scm:purchase:edit')")
    @OperLog(title = "ScmPurchaseOrder", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody ScmPurchaseOrder entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('scm:purchase:remove')")
    @OperLog(title = "ScmPurchaseOrder", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "提交")
    @PreAuthorize("@ps.hasPermi('scm:purchase:submit')")
    @OperLog(title = "采购订单-提交", businessType = 2)
    @PutMapping("submit/{id}")
    public R<Integer> submit(@PathVariable Long id) { return service.changeStatus(id, 1) ? R.ok(1) : R.fail("提交失败"); }

    @Operation(summary = "审核")
    @PreAuthorize("@ps.hasPermi('scm:purchase:audit')")
    @OperLog(title = "采购订单-审核", businessType = 2)
    @PutMapping("audit/{id}")
    public R<Integer> audit(@PathVariable Long id) { return service.changeStatus(id, 2) ? R.ok(1) : R.fail("审核失败"); }

    @Operation(summary = "下达(已审核→给供应商)")
    @PreAuthorize("@ps.hasPermi('scm:purchase:release')")
    @OperLog(title = "采购订单-下达", businessType = 2)
    @PutMapping("release/{id}")
    public R<Integer> release(@PathVariable Long id) { return service.changeStatus(id, 3) ? R.ok(1) : R.fail("下达失败"); }

    @Operation(summary = "完成")
    @PreAuthorize("@ps.hasPermi('scm:purchase:close')")
    @OperLog(title = "采购订单-完成", businessType = 2)
    @PutMapping("complete/{id}")
    public R<Integer> complete(@PathVariable Long id) { return service.changeStatus(id, 6) ? R.ok(1) : R.fail("完成失败"); }

    @Operation(summary = "驳回")
    @PreAuthorize("@ps.hasPermi('scm:purchase:reject')")
    @OperLog(title = "采购订单-驳回", businessType = 2)
    @PutMapping("reject/{id}")
    public R<Integer> reject(@PathVariable Long id) { return service.changeStatus(id, 7) ? R.ok(1) : R.fail("驳回失败"); }

    @Operation(summary = "取消")
    @PreAuthorize("@ps.hasPermi('scm:purchase:cancel')")
    @OperLog(title = "采购订单-取消", businessType = 2)
    @PutMapping("cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id) { return service.changeStatus(id, 9) ? R.ok(1) : R.fail("取消失败"); }

    /**
     * P1: 生成采购入库单（可重复，直到全部到货）
     * Body: { lines: [ {lineId, qty, batchNo?, unitPrice?}, ... ] }
     * 返回: R.data = stockInId
     */
    @Operation(summary = "P1 生成采购入库单 (部分/全部 多次入库)")
    @PreAuthorize("@ps.hasPermi('scm:purchase:stockIn')")
    @OperLog(title = "采购订单生成入库单", businessType = 4)
    @PutMapping("stockIn/{id}")
    public R<Long> stockIn(@PathVariable("id") Long poId,
                           @RequestBody Map<String, Object> body) {
        Long stockInId = service.generateStockIn(poId, body);
        return stockInId == null ? R.fail("生成入库单失败") : R.ok(stockInId);
    }

    @Operation(summary = "下拉选项")
    @PreAuthorize("@ps.hasPermi('scm:purchase:list')")
    @GetMapping("/options")
    public R<List<ScmPurchaseOrder>> options() { return R.ok(service.options()); }

    @Operation(summary = "行项明细分页")
    @PreAuthorize("@ps.hasPermi('scm:purchase:list')")
    @GetMapping("/{id}/lines")
    public R<TableDataInfo> lines(@PathVariable Long id,
                                  @RequestParam(defaultValue = "1") int pageNum,
                                  @RequestParam(defaultValue = "50") int pageSize,
                                  @RequestParam(required = false) String materialCode,
                                  @RequestParam(required = false) String materialName) {
        IPage<ScmPurchaseOrderLine> p = lineService.selectPage(pageNum, pageSize, id, materialCode, materialName);
        TableDataInfo info = new TableDataInfo();
        info.setRows(p.getRecords()); info.setTotal(p.getTotal()); info.setCode(200); info.setMsg("查询成功");
        return R.ok(info);
    }
}
