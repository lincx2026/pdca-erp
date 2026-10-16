package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.PmmWorkOrderBom;
import com.pcba.erp.pmm.service.IPmmWorkOrderBomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "工单BOM管理")
@RestController
@RequestMapping("/pm/work-bom")
@RequiredArgsConstructor
public class PmmWorkOrderBomController {

    private final IPmmWorkOrderBomService service;

    @Operation(summary = "工单BOM分页")
    @PreAuthorize("@ps.hasPermi('pm:workbom:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) Long workOrderId,
                                 @RequestParam(required = false) String materialCode,
                                 @RequestParam(required = false) String materialName) {
        IPage<PmmWorkOrderBom> page = service.selectPage(pageNum, pageSize, workOrderId, materialCode, materialName);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "工单BOM详情")
    @PreAuthorize("@ps.hasPermi('pm:workbom:list')")
    @GetMapping("/{id}")
    public R<PmmWorkOrderBom> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增BOM行")
    @PreAuthorize("@ps.hasPermi('pm:workbom:add')")
    @OperLog(title = "工单BOM", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody PmmWorkOrderBom entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改BOM行")
    @PreAuthorize("@ps.hasPermi('pm:workbom:edit')")
    @OperLog(title = "工单BOM", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody PmmWorkOrderBom entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除BOM行")
    @PreAuthorize("@ps.hasPermi('pm:workbom:remove')")
    @OperLog(title = "工单BOM", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "按工单获取全部BOM行")
    @PreAuthorize("@ps.hasPermi('pm:workbom:explode')")
    @GetMapping("/listByWorkOrder")
    public R<List<PmmWorkOrderBom>> listByWorkOrder(@RequestParam Long workOrderId) {
        return R.ok(service.listByWorkOrder(workOrderId));
    }
}
