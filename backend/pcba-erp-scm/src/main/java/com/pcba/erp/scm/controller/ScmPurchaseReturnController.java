package com.pcba.erp.scm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.scm.entity.ScmPurchaseReturn;
import com.pcba.erp.scm.entity.ScmPurchaseReturnLine;
import com.pcba.erp.scm.service.IScmPurchaseReturnLineService;
import com.pcba.erp.scm.service.IScmPurchaseReturnService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "采购-return")
@RestController
@RequestMapping("/scm/return")
@RequiredArgsConstructor
public class ScmPurchaseReturnController {

    private final IScmPurchaseReturnService service;
    private final IScmPurchaseReturnLineService lineService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('scm:return:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String returnNo, @RequestParam(required = false) Long poId, @RequestParam(required = false) Long supplierId, @RequestParam(required = false) String supplierName, @RequestParam(required = false) Long warehouseId, @RequestParam(required = false) Integer status, @RequestParam(required = false) String returnDate) {
        IPage<ScmPurchaseReturn> page = service.selectPage(pageNum, pageSize, returnNo, poId, supplierId, supplierName, warehouseId, status, returnDate);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('scm:return:list')")
    @GetMapping("/{id}")
    public R<ScmPurchaseReturn> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增(仅头)")
    @PreAuthorize("@ps.hasPermi('scm:return:add')")
    @OperLog(title = "ScmPurchaseReturn", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody ScmPurchaseReturn entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改(仅头)")
    @PreAuthorize("@ps.hasPermi('scm:return:edit')")
    @OperLog(title = "ScmPurchaseReturn", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody ScmPurchaseReturn entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('scm:return:remove')")
    @OperLog(title = "ScmPurchaseReturn", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "提交(→1)")
    @PreAuthorize("@ps.hasPermi('scm:return:submit')")
    @OperLog(title = "提交", businessType = 2)
    @PutMapping("submit/{id}")
    public R<Integer> submit(@PathVariable Long id) { return service.changeStatus(id, 1) ? R.ok(1) : R.fail("提交失败"); }

    @Operation(summary = "审核(仅推进状态到2，不含WMS出库)")
    @PreAuthorize("@ps.hasPermi('scm:return:audit')")
    @OperLog(title = "审核", businessType = 2)
    @PutMapping("audit/{id}")
    public R<Integer> audit(@PathVariable Long id) { return service.changeStatus(id, 2) ? R.ok(1) : R.fail("审核失败"); }

    @Operation(summary = "生成WMS采购出库单（返回新出库单ID；状态≥2方可；回填stock_out_id并推进状态=3出库中）")
    @PreAuthorize("@ps.hasPermi('scm:return:stockout')")
    @OperLog(title = "生成出库单", businessType = 2)
    @PutMapping("stockOut/{id}")
    public R<Long> stockOut(@PathVariable Long id) {
        Long stockOutId = service.generateStockOut(id);
        return stockOutId == null ? R.fail("生成出库单失败") : R.ok(stockOutId);
    }

    @Operation(summary = "供方签收")
    @PreAuthorize("@ps.hasPermi('scm:return:sign')")
    @OperLog(title = "供方签收", businessType = 2)
    @PutMapping("sign/{id}")
    public R<Integer> sign(@PathVariable Long id) { return service.changeStatus(id, 5) ? R.ok(1) : R.fail("供方签收失败"); }

    @Operation(summary = "取消")
    @PreAuthorize("@ps.hasPermi('scm:return:cancel')")
    @OperLog(title = "取消", businessType = 2)
    @PutMapping("cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id) { return service.changeStatus(id, 9) ? R.ok(1) : R.fail("取消失败"); }

    @Operation(summary = "下拉选项")
    @PreAuthorize("@ps.hasPermi('scm:return:list')")
    @GetMapping("/options")
    public R<List<ScmPurchaseReturn>> options() { return R.ok(service.options()); }

    @Operation(summary = "行项明细分页")
    @PreAuthorize("@ps.hasPermi('scm:return:list')")
    @GetMapping("/{id}/lines")
    public R<TableDataInfo> lines(@PathVariable Long id,
                                  @RequestParam(defaultValue = "1") int pageNum,
                                  @RequestParam(defaultValue = "50") int pageSize,
                                  @RequestParam(required = false) String materialCode,
                                  @RequestParam(required = false) String materialName) {
        IPage<ScmPurchaseReturnLine> p = lineService.selectPage(pageNum, pageSize, id, materialCode, materialName);
        TableDataInfo info = new TableDataInfo();
        info.setRows(p.getRecords()); info.setTotal(p.getTotal()); info.setCode(200); info.setMsg("查询成功");
        return R.ok(info);
    }

    /* ================== T4 新端点 ================== */

    /**
     * 保存退货单(含行)
     * body: { header: ScmPurchaseReturn, lines: ScmPurchaseReturnLine[] }
     */
    @Operation(summary = "保存退货单(含行)-校验剩余可退-返回returnId")
    @PreAuthorize("@ps.hasPermi('scm:return:add') or @ps.hasPermi('scm:return:edit')")
    @OperLog(title = "保存退货单(含行)", businessType = 1)
    @PostMapping("/save")
    public R<Long> save(@RequestBody Map<String, Object> body) {
        if (body == null || body.isEmpty()) return R.bad("请求体不能为空");
        try {
            Object h = body.get("header");
            Object l = body.get("lines");
            if (h == null) return R.bad("缺少 header 字段");
            if (!(l instanceof List)) return R.bad("缺少 lines 数组");
            ScmPurchaseReturn header = objectMapper.convertValue(h, ScmPurchaseReturn.class);
            List<ScmPurchaseReturnLine> lines = objectMapper.convertValue(l, new TypeReference<List<ScmPurchaseReturnLine>>() {});
            Long returnId = service.saveReturn(header, lines);
            return returnId == null ? R.fail("保存失败") : R.ok(returnId);
        } catch (ServiceException se) {
            return R.fail(se.getMessage());
        } catch (IllegalArgumentException iae) {
            return R.bad("参数格式错误: " + iae.getMessage());
        }
    }

    /**
     * 审核通过：头 status0/1→2，生成WMS出库并推进到3确认，发布StockOutConfirmedEvent，
     * 回写PO行returnedQty，若hasReplenishment=1生成请购补单；最后返回摘要
     */
    @Operation(summary = "退货单审核通过(自动WMS出库+事件+PO回写+可选补请购)")
    @PreAuthorize("@ps.hasPermi('scm:return:audit')")
    @OperLog(title = "退货审核通过-联动WMS/PO/请购", businessType = 2)
    @PostMapping("/{returnId}/audit-pass")
    public R<Map<String, Object>> auditPass(@PathVariable Long returnId) {
        try {
            Map<String, Object> result = service.auditPass(returnId);
            return R.ok(result);
        } catch (ServiceException se) {
            return R.fail(se.getMessage());
        }
    }

    /**
     * 前端从入库单选择行 → 生成草稿退货单（按stockIn分组→一stockIn→一退货单）
     * body: {warehouseId?, stockInLineIds:[Long], returnReason:String, hasReplenishment:0/1}
     */
    @Operation(summary = "从入库单行选择生成草稿退货单(按stockIn分组，返回returnId列表)")
    @PreAuthorize("@ps.hasPermi('scm:return:add')")
    @OperLog(title = "按入库单行生成退货草稿", businessType = 1)
    @PostMapping("/from-stock-in-lines")
    public R<List<Long>> fromStockInLines(@RequestBody Map<String, Object> body) {
        try {
            List<Long> ids = service.fromStockInLines(body);
            return R.ok(ids);
        } catch (ServiceException se) {
            return R.fail(se.getMessage());
        }
    }
}
