package com.pcba.erp.scm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.scm.entity.ScmSupplierPrice;
import com.pcba.erp.scm.service.IScmSupplierPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "采购-price")
@RestController
@RequestMapping("/scm/price")
@RequiredArgsConstructor
public class ScmSupplierPriceController {

    private final IScmSupplierPriceService service;

    @Operation(summary = "分页查询 (按物料分组, 同物料完整同页)")
    @PreAuthorize("@ps.hasPermi('scm:price:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) Long supplierId,
                                 @RequestParam(required = false) Long materialId,
                                 @RequestParam(required = false) String materialCode,
                                 @RequestParam(required = false) String materialName,
                                 @RequestParam(required = false) String supplierName,
                                 @RequestParam(required = false) String currency,
                                 @RequestParam(required = false) Integer status) {
        IPage<ScmSupplierPrice> page = service.selectGroupedPage(pageNum, pageSize, supplierId, materialId,
                materialCode, materialName, supplierName, currency, status);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords()); info.setTotal(page.getTotal()); info.setCode(200); info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('scm:price:list')")
    @GetMapping("/{id}")
    public R<ScmSupplierPrice> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('scm:price:add')")
    @OperLog(title = "ScmSupplierPrice", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody ScmSupplierPrice entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('scm:price:edit')")
    @OperLog(title = "ScmSupplierPrice", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody ScmSupplierPrice entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('scm:price:remove')")
    @OperLog(title = "ScmSupplierPrice", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "提交 (status 0→1)")
    @PreAuthorize("@ps.hasPermi('scm:price:submit')")
    @OperLog(title = "供应商报价-提交", businessType = 2)
    @PutMapping("submit/{id}")
    public R<Integer> submit(@PathVariable Long id) { return service.submit(id) ? R.ok(1) : R.fail("提交失败"); }

    @Operation(summary = "审核 (status 1→2生效)")
    @PreAuthorize("@ps.hasPermi('scm:price:audit')")
    @OperLog(title = "供应商报价-审核", businessType = 2)
    @PutMapping("audit/{id}")
    public R<Integer> audit(@PathVariable Long id)  { return service.audit(id) ? R.ok(1) : R.fail("审核失败"); }

    @Operation(summary = "停用")
    @PreAuthorize("@ps.hasPermi('scm:price:disable')")
    @OperLog(title = "供应商报价-停用", businessType = 2)
    @PutMapping("disable/{id}")
    public R<Integer> disable(@PathVariable Long id){ return service.disable(id) ? R.ok(1) : R.fail("停用失败"); }

    @Operation(summary = "启用 (快速=直接进入生效)")
    @PreAuthorize("@ps.hasPermi('scm:price:enable')")
    @OperLog(title = "供应商报价-启用", businessType = 2)
    @PutMapping("enable/{id}")
    public R<Integer> enable(@PathVariable Long id) { return service.changeStatus(id, 2) ? R.ok(1) : R.fail("启用失败"); }

    @Operation(summary = "设为默认")
    @PreAuthorize("@ps.hasPermi('scm:price:default')")
    @OperLog(title = "设为默认", businessType = 2)
    @PutMapping("setDefault/{id}")
    public R<Integer> setDefault(@PathVariable Long id, @RequestBody(required = false) Map<String,Object> body) {
        ScmSupplierPrice inst = service.getDetail(id);
        Long mid = inst.getMaterialId();
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ScmSupplierPrice> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        uw.eq(ScmSupplierPrice::getMaterialId, mid).eq(ScmSupplierPrice::getIsDefault, 1).set(ScmSupplierPrice::getIsDefault, 0);
        com.pcba.erp.scm.mapper.ScmSupplierPriceMapper m = com.pcba.erp.common.utils.SpringUtils.getBean(com.pcba.erp.scm.mapper.ScmSupplierPriceMapper.class);
        m.update(null, uw);
        inst.setIsDefault(1); m.updateById(inst);
        return R.ok(1);
    }

    /**
     * P0: 批量把生效报价(status=2)转成采购订单
     * Body: { supplierId, items:[{priceId, qty, deliveryDate?}],
     *         extra:{ poDate?, deliveryDate?, warehouseId?, buyerId?,
     *                  paymentTerm?, currency?, remark?, reqSource?, poType?, bizCategory? } }
     */
    @Operation(summary = "P0 批量转采购订单 (生效报价→PO)")
    @PreAuthorize("@ps.hasPermi('scm:price:convert')")
    @OperLog(title = "供应商报价转采购订单", businessType = 4)
    @PostMapping("convert-order")
    public R<Long> convertOrder(@RequestBody Map<String, Object> body) {
        Long sid = body.get("supplierId") == null ? null : Long.valueOf(body.get("supplierId").toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) body.getOrDefault("extra", new java.util.HashMap<>());
        Long poId = service.convertToPurchaseOrder(sid, items, extra);
        return poId == null ? R.fail("转采购订单失败") : R.ok(poId);
    }

    @Operation(summary = "下拉选项")
    @PreAuthorize("@ps.hasPermi('scm:price:list')")
    @GetMapping("/options")
    public R<List<ScmSupplierPrice>> options() { return R.ok(service.options()); }
}
