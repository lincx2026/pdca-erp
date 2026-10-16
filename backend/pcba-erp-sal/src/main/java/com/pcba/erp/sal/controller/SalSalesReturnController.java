package com.pcba.erp.sal.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.sal.entity.SalSalesReturn;
import com.pcba.erp.sal.entity.SalSalesReturnLine;
import com.pcba.erp.sal.service.ISalSalesReturnLineService;
import com.pcba.erp.sal.service.ISalSalesReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "销售-sales-return")
@RestController
@RequestMapping("/sal/return")
@RequiredArgsConstructor
public class SalSalesReturnController {

    private final ISalSalesReturnService service;
    private final ISalSalesReturnLineService lineService;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('sal:return:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String returnNo, @RequestParam(required = false) Long customerId, @RequestParam(required = false) String customerName, @RequestParam(required = false) Long soId, @RequestParam(required = false) Integer status, @RequestParam(required = false) String returnDate) {
        IPage<SalSalesReturn> page = service.selectPage(pageNum, pageSize, returnNo, customerId, customerName, soId, status, returnDate);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('sal:return:list')")
    @GetMapping("/{id}")
    public R<SalSalesReturn> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('sal:return:add')")
    @OperLog(title = "SalSalesReturn", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody SalSalesReturn entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('sal:return:edit')")
    @OperLog(title = "SalSalesReturn", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody SalSalesReturn entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('sal:return:remove')")
    @OperLog(title = "SalSalesReturn", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "提交")
    @PreAuthorize("@ps.hasPermi('sal:return:submit')")
    @OperLog(title = "提交", businessType = 2)
    @PutMapping("submit/{id}")
    public R<Integer> submit(@PathVariable Long id) { return service.changeStatus(id, 1) ? R.ok(1) : R.fail("提交失败"); }
    @Operation(summary = "审核")
    @PreAuthorize("@ps.hasPermi('sal:return:audit')")
    @OperLog(title = "审核", businessType = 2)
    @PutMapping("audit/{id}")
    public R<Integer> audit(@PathVariable Long id) { return service.changeStatus(id, 2) ? R.ok(1) : R.fail("审核失败"); }
    @Operation(summary = "生成WMS退货入库单（返回新入库单ID；仅状态=2方可；回填stock_in_id并推进状态=3入库中）")
    @PreAuthorize("@ps.hasPermi('sal:return:stockin')")
    @OperLog(title = "生成入库单", businessType = 2)
    @PutMapping("stockIn/{id}")
    public R<Long> stockIn(@PathVariable Long id) {
        Long stockInId = service.generateStockIn(id);
        return stockInId == null ? R.fail("生成入库单失败") : R.ok(stockInId);
    }
    @Operation(summary = "完成")
    @PreAuthorize("@ps.hasPermi('sal:return:close')")
    @OperLog(title = "完成", businessType = 2)
    @PutMapping("complete/{id}")
    public R<Integer> complete(@PathVariable Long id) { return service.changeStatus(id, 4) ? R.ok(1) : R.fail("完成失败"); }

    /** 退货质检录入请求体 */
    public record QcRequest(String disposition, String qcRemark, List<SalSalesReturnLine> lines) {}

    @Operation(summary = "退货质检录入(入库确认后待检→录入合格数/处置→推进3→4并重算退款)")
    @PreAuthorize("@ps.hasPermi('sal:return:qc')")
    @OperLog(title = "退货质检", businessType = 2)
    @PutMapping("qc/{id}")
    public R<Integer> submitQc(@PathVariable Long id, @RequestBody QcRequest req) {
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean ok = service.submitQc(id, req.disposition(), req.qcRemark(), req.lines(), operator);
        return ok ? R.ok(1) : R.fail("质检录入失败");
    }
    @Operation(summary = "驳回")
    @PreAuthorize("@ps.hasPermi('sal:return:reject')")
    @OperLog(title = "驳回", businessType = 2)
    @PutMapping("reject/{id}")
    public R<Integer> reject(@PathVariable Long id) { return service.changeStatus(id, 7) ? R.ok(1) : R.fail("驳回失败"); }
    @Operation(summary = "取消")
    @PreAuthorize("@ps.hasPermi('sal:return:cancel')")
    @OperLog(title = "取消", businessType = 2)
    @PutMapping("cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id) { return service.changeStatus(id, 9) ? R.ok(1) : R.fail("取消失败"); }

    @Operation(summary = "下拉选项")
    @PreAuthorize("@ps.hasPermi('sal:return:list')")
    @GetMapping("/options")
    public R<List<SalSalesReturn>> options() { return R.ok(service.options()); }

    @Operation(summary = "行项明细分页")
    @PreAuthorize("@ps.hasPermi('sal:return:list')")
    @GetMapping("/{id}/lines")
    public R<TableDataInfo> lines(@PathVariable Long id,
                                  @RequestParam(defaultValue = "1") int pageNum,
                                  @RequestParam(defaultValue = "50") int pageSize,
                                  @RequestParam(required = false) String materialCode,
                                  @RequestParam(required = false) String materialName) {
        IPage<SalSalesReturnLine> p = lineService.selectPage(pageNum, pageSize, id, materialCode, materialName);
        TableDataInfo info = new TableDataInfo();
        info.setRows(p.getRecords()); info.setTotal(p.getTotal()); info.setCode(200); info.setMsg("查询成功");
        return R.ok(info);
    }
}
