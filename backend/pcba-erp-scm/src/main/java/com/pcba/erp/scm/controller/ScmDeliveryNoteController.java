package com.pcba.erp.scm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.scm.entity.ScmDeliveryNote;
import com.pcba.erp.scm.entity.ScmDeliveryNoteLine;
import com.pcba.erp.scm.service.IScmDeliveryNoteLineService;
import com.pcba.erp.scm.service.IScmDeliveryNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "采购-delivery")
@RestController
@RequestMapping("/scm/delivery")
@RequiredArgsConstructor
public class ScmDeliveryNoteController {

    private final IScmDeliveryNoteService service;
    private final IScmDeliveryNoteLineService lineService;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('scm:delivery:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String deliveryNo, @RequestParam(required = false) String poNo, @RequestParam(required = false) Long supplierId, @RequestParam(required = false) String supplierName, @RequestParam(required = false) Long warehouseId, @RequestParam(required = false) Integer status, @RequestParam(required = false) String deliveryDate) {
        IPage<ScmDeliveryNote> page = service.selectPage(pageNum, pageSize, deliveryNo, poNo, supplierId, supplierName, warehouseId, status, deliveryDate);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('scm:delivery:list')")
    @GetMapping("/{id}")
    public R<ScmDeliveryNote> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('scm:delivery:add')")
    @OperLog(title = "ScmDeliveryNote", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody ScmDeliveryNote entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('scm:delivery:edit')")
    @OperLog(title = "ScmDeliveryNote", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody ScmDeliveryNote entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('scm:delivery:remove')")
    @OperLog(title = "ScmDeliveryNote", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "收货登记")
    @PreAuthorize("@ps.hasPermi('scm:delivery:receive')")
    @OperLog(title = "收货登记", businessType = 2)
    @PutMapping("receive/{id}")
    public R<Integer> receive(@PathVariable Long id) { return service.changeStatus(id, 2) ? R.ok(1) : R.fail("收货登记失败"); }
    @Operation(summary = "IQC检验")
    @PreAuthorize("@ps.hasPermi('scm:delivery:iqc')")
    @OperLog(title = "IQC检验", businessType = 2)
    @PutMapping("iqc/{id}")
    public R<Integer> iqc(@PathVariable Long id) { return service.changeStatus(id, 3) ? R.ok(1) : R.fail("IQC检验失败"); }
    @Operation(summary = "生成入库单")
    @PreAuthorize("@ps.hasPermi('scm:delivery:stockin')")
    @OperLog(title = "生成入库单", businessType = 2)
    @PutMapping("stockIn/{id}")
    public R<Long> stockIn(@PathVariable Long id) {
        Long stockInId = service.generateStockIn(id);
        return stockInId == null ? R.fail("生成入库单失败") : R.ok(stockInId);
    }
    @Operation(summary = "取消")
    @PreAuthorize("@ps.hasPermi('scm:delivery:cancel')")
    @OperLog(title = "取消", businessType = 2)
    @PutMapping("cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id) { return service.changeStatus(id, 9) ? R.ok(1) : R.fail("取消失败"); }

    @Operation(summary = "下拉选项")
    @PreAuthorize("@ps.hasPermi('scm:delivery:list')")
    @GetMapping("/options")
    public R<List<ScmDeliveryNote>> options() { return R.ok(service.options()); }

    @Operation(summary = "行项明细分页")
    @PreAuthorize("@ps.hasPermi('scm:delivery:list')")
    @GetMapping("/{id}/lines")
    public R<TableDataInfo> lines(@PathVariable Long id,
                                  @RequestParam(defaultValue = "1") int pageNum,
                                  @RequestParam(defaultValue = "50") int pageSize,
                                  @RequestParam(required = false) String materialCode,
                                  @RequestParam(required = false) String materialName) {
        IPage<ScmDeliveryNoteLine> p = lineService.selectPage(pageNum, pageSize, id, materialCode, materialName);
        TableDataInfo info = new TableDataInfo();
        info.setRows(p.getRecords()); info.setTotal(p.getTotal()); info.setCode(200); info.setMsg("查询成功");
        return R.ok(info);
    }

    /* ============ QC 分流 / 快速到货 ============ */

    @Operation(summary = "从采购订单快速生成到货通知单草稿")
    @PreAuthorize("@ps.hasPermi('scm:delivery:add')")
    @OperLog(title = "从PO生成到货单", businessType = 1)
    @PostMapping("/from-purchase-order")
    public R<Long> fromPo(@RequestBody Map<String,Object> body) {
        Object poIdObj = body.get("poId");
        if (poIdObj == null) throw new ServiceException("poId不能为空");
        Long poId;
        try {
            poId = Long.valueOf(poIdObj.toString());
        } catch (NumberFormatException e) {
            throw new ServiceException("poId格式非法");
        }
        @SuppressWarnings("unchecked")
        Map<String,Object> extra = (Map<String,Object>) body.get("headerExtra");
        Long id = service.createFromPurchaseOrder(poId, extra);
        return id == null ? R.fail("生成到货单失败") : R.ok(id);
    }

    @Operation(summary = "IQC QC判定(含分流：调拨/退货草稿/报废)")
    @PreAuthorize("@ps.hasPermi('scm:delivery:iqc')")
    @OperLog(title = "IQC QC判定", businessType = 2)
    @PostMapping("/{deliveryId}/qc-judge")
    public R<Map<String,Object>> qcJudge(@PathVariable Long deliveryId, @RequestBody Map<String,Object> body) {
        Object listObj = body.get("judgements");
        if (!(listObj instanceof List)) throw new ServiceException("judgements数组不能为空");
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> judgements = (List<Map<String,Object>>) listObj;
        Map<String,Object> result = service.qcJudge(deliveryId, judgements);
        return R.ok(result);
    }
}
