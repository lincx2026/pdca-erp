package com.pcba.erp.scm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.scm.entity.ScmPurchaseRequisition;
import com.pcba.erp.scm.entity.ScmPurchaseRequisitionLine;
import com.pcba.erp.scm.service.IScmPurchaseRequisitionLineService;
import com.pcba.erp.scm.service.IScmPurchaseRequisitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "采购-requisition")
@RestController
@RequestMapping("/scm/requisition")
@RequiredArgsConstructor
public class ScmPurchaseRequisitionController {

    private final IScmPurchaseRequisitionService service;
    private final IScmPurchaseRequisitionLineService lineService;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('scm:requisition:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String requisitionNo, @RequestParam(required = false) String title, @RequestParam(required = false) String applicantName, @RequestParam(required = false) Long deptId, @RequestParam(required = false) Integer status, @RequestParam(required = false) String requiredDate) {
        IPage<ScmPurchaseRequisition> page = service.selectPage(pageNum, pageSize, requisitionNo, title, applicantName, deptId, status, requiredDate);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('scm:requisition:list')")
    @GetMapping("/{id}")
    public R<ScmPurchaseRequisition> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('scm:requisition:add')")
    @OperLog(title = "ScmPurchaseRequisition", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody ScmPurchaseRequisition entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('scm:requisition:edit')")
    @OperLog(title = "ScmPurchaseRequisition", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody ScmPurchaseRequisition entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('scm:requisition:remove')")
    @OperLog(title = "ScmPurchaseRequisition", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "提交")
    @PreAuthorize("@ps.hasPermi('scm:requisition:submit')")
    @OperLog(title = "提交", businessType = 2)
    @PutMapping("submit/{id}")
    public R<Integer> submit(@PathVariable Long id) { return service.changeStatus(id, 1) ? R.ok(1) : R.fail("提交失败"); }
    @Operation(summary = "审核")
    @PreAuthorize("@ps.hasPermi('scm:requisition:audit')")
    @OperLog(title = "审核", businessType = 2)
    @PutMapping("audit/{id}")
    public R<Integer> audit(@PathVariable Long id) { return service.changeStatus(id, 2) ? R.ok(1) : R.fail("审核失败"); }
    @Operation(summary = "驳回")
    @PreAuthorize("@ps.hasPermi('scm:requisition:reject')")
    @OperLog(title = "驳回", businessType = 2)
    @PutMapping("reject/{id}")
    public R<Integer> reject(@PathVariable Long id) { return service.changeStatus(id, 4) ? R.ok(1) : R.fail("驳回失败"); }
    @Operation(summary = "转采购单")
    @PreAuthorize("@ps.hasPermi('scm:requisition:convert')")
    @OperLog(title = "转采购单", businessType = 2)
    @PutMapping("convert/{id}")
    public R<Integer> convert(@PathVariable Long id) { return service.changeStatus(id, 3) ? R.ok(1) : R.fail("转采购单失败"); }
    @Operation(summary = "取消")
    @PreAuthorize("@ps.hasPermi('scm:requisition:cancel')")
    @OperLog(title = "取消", businessType = 2)
    @PutMapping("cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id) { return service.changeStatus(id, 9) ? R.ok(1) : R.fail("取消失败"); }

    @Operation(summary = "下拉选项")
    @PreAuthorize("@ps.hasPermi('scm:requisition:list')")
    @GetMapping("/options")
    public R<List<ScmPurchaseRequisition>> options() { return R.ok(service.options()); }

    // ============ T5 ============

    @Operation(summary = "T5-2 安全库存短缺→批量生成请购行(生成或追加到当日本地草稿)")
    @PreAuthorize("@ps.hasPermi('scm:requisition:add')")
    @OperLog(title = "安全库存短缺请购", businessType = 4)
    @PostMapping("/generate-shortfall")
    public R<Long> generateShortfall(@RequestBody(required = false) Map<String, Object> options) {
        Long id = service.generateFromSafetyStock(options);
        return id == null ? R.fail("生成/追加失败") : R.ok(id);
    }

    @Operation(summary = "T5-3 请购已审核→批量转采购PO (按默认供方+阶梯价合并)")
    @PreAuthorize("@ps.hasPermi('scm:requisition:convert')")
    @OperLog(title = "批量转采购PO", businessType = 4)
    @PostMapping("/convert-purchase-order")
    public R<Map<String, Object>> convertPurchaseOrder(@RequestBody Map<String, Object> body) {
        if (body == null || !body.containsKey("lineIds")) {
            return R.fail("lineIds 必传");
        }
        Object lineObj = body.get("lineIds");
        List<Long> lineIds = new java.util.ArrayList<>();
        if (lineObj instanceof Iterable) {
            for (Object o : (Iterable<?>) lineObj) {
                try { lineIds.add(Long.valueOf(String.valueOf(o))); } catch (Exception ignore) {}
            }
        }
        Map<String, Object> common = new java.util.HashMap<>(body);
        common.remove("lineIds");
        return R.ok(service.convertToPurchaseOrder(lineIds, common));
    }

    @Operation(summary = "行项明细分页")
    @PreAuthorize("@ps.hasPermi('scm:requisition:list')")
    @GetMapping("/{id}/lines")
    public R<TableDataInfo> lines(@PathVariable Long id,
                                  @RequestParam(defaultValue = "1") int pageNum,
                                  @RequestParam(defaultValue = "50") int pageSize,
                                  @RequestParam(required = false) String materialCode,
                                  @RequestParam(required = false) String materialName) {
        IPage<ScmPurchaseRequisitionLine> p = lineService.selectPage(pageNum, pageSize, id, materialCode, materialName);
        TableDataInfo info = new TableDataInfo();
        info.setRows(p.getRecords()); info.setTotal(p.getTotal()); info.setCode(200); info.setMsg("查询成功");
        return R.ok(info);
    }
}
