package com.pcba.erp.sal.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.sal.entity.SalSalesOrder;
import com.pcba.erp.sal.entity.SalSalesOrderLine;
import com.pcba.erp.sal.service.ISalSalesOrderLineService;
import com.pcba.erp.sal.service.ISalSalesOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "销售-sales-order")
@RestController
@RequestMapping("/sal/order")
@RequiredArgsConstructor
public class SalSalesOrderController {

    private final ISalSalesOrderService service;
    private final ISalSalesOrderLineService lineService;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('sal:order:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String soNo, @RequestParam(required = false) String soType, @RequestParam(required = false) Long customerId, @RequestParam(required = false) String customerName, @RequestParam(required = false) Long salesRepId, @RequestParam(required = false) Integer status, @RequestParam(required = false) String deliveryDate) {
        IPage<SalSalesOrder> page = service.selectPage(pageNum, pageSize, soNo, soType, customerId, customerName, salesRepId, status, deliveryDate);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('sal:order:list')")
    @GetMapping("/{id}")
    public R<SalSalesOrder> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('sal:order:add')")
    @OperLog(title = "SalSalesOrder", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody SalSalesOrder entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('sal:order:edit')")
    @OperLog(title = "SalSalesOrder", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody SalSalesOrder entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('sal:order:remove')")
    @OperLog(title = "SalSalesOrder", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "提交")
    @PreAuthorize("@ps.hasPermi('sal:order:submit')")
    @OperLog(title = "提交", businessType = 2)
    @PutMapping("submit/{id}")
    public R<Integer> submit(@PathVariable Long id) { return service.changeStatus(id, 1) ? R.ok(1) : R.fail("提交失败"); }
    @Operation(summary = "审核")
    @PreAuthorize("@ps.hasPermi('sal:order:audit')")
    @OperLog(title = "审核", businessType = 2)
    @PutMapping("audit/{id}")
    public R<Integer> audit(@PathVariable Long id) { return service.changeStatus(id, 2) ? R.ok(1) : R.fail("审核失败"); }
    @Operation(summary = "完成")
    @PreAuthorize("@ps.hasPermi('sal:order:close')")
    @OperLog(title = "完成", businessType = 2)
    @PutMapping("complete/{id}")
    public R<Integer> complete(@PathVariable Long id) { return service.changeStatus(id, 6) ? R.ok(1) : R.fail("完成失败"); }
    @Operation(summary = "驳回")
    @PreAuthorize("@ps.hasPermi('sal:order:reject')")
    @OperLog(title = "驳回", businessType = 2)
    @PutMapping("reject/{id}")
    public R<Integer> reject(@PathVariable Long id) { return service.changeStatus(id, 7) ? R.ok(1) : R.fail("驳回失败"); }
    @Operation(summary = "取消")
    @PreAuthorize("@ps.hasPermi('sal:order:cancel')")
    @OperLog(title = "取消", businessType = 2)
    @PutMapping("cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id) { return service.changeStatus(id, 9) ? R.ok(1) : R.fail("取消失败"); }

    @Operation(summary = "生成WMS出库单(返回stockOutId)，状态2→3已发货/在途")
    @PreAuthorize("@ps.hasPermi('sal:order:stockout')")
    @OperLog(title = "生成出库单", businessType = 2)
    @PutMapping("stockOut/{id}")
    public R<Long> stockOut(@PathVariable Long id) {
        Long stockOutId = service.generateStockOut(id);
        return stockOutId == null ? R.fail("生成出库单失败") : R.ok(stockOutId);
    }

    @Operation(summary = "下拉选项")
    @PreAuthorize("@ps.hasPermi('sal:order:list')")
    @GetMapping("/options")
    public R<List<SalSalesOrder>> options() { return R.ok(service.options()); }

    @Operation(summary = "行项明细分页")
    @PreAuthorize("@ps.hasPermi('sal:order:list')")
    @GetMapping("/{id}/lines")
    public R<TableDataInfo> lines(@PathVariable Long id,
                                  @RequestParam(defaultValue = "1") int pageNum,
                                  @RequestParam(defaultValue = "50") int pageSize,
                                  @RequestParam(required = false) String materialCode,
                                  @RequestParam(required = false) String materialName) {
        IPage<SalSalesOrderLine> p = lineService.selectPage(pageNum, pageSize, id, materialCode, materialName);
        TableDataInfo info = new TableDataInfo();
        info.setRows(p.getRecords()); info.setTotal(p.getTotal()); info.setCode(200); info.setMsg("查询成功");
        return R.ok(info);
    }
}
