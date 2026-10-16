package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.PmmQualityDefect;
import com.pcba.erp.pmm.service.IPmmQualityDefectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "质量不良管理")
@RestController
@RequestMapping("/pm/quality")
@RequiredArgsConstructor
public class PmmQualityDefectController {

    private final IPmmQualityDefectService service;

    @Operation(summary = "不良记录分页")
    @PreAuthorize("@ps.hasPermi('pm:quality:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) Long workOrderId,
                                 @RequestParam(required = false) String defectCode,
                                 @RequestParam(required = false) String defectLevel,
                                 @RequestParam(required = false) String checkType,
                                 @RequestParam(required = false) String disposition,
                                 @RequestParam(required = false) String processName,
                                 @RequestParam(required = false) Long inspectorId) {
        IPage<PmmQualityDefect> page = service.selectPage(pageNum, pageSize, workOrderId, defectCode, defectLevel,
                checkType, disposition, processName, inspectorId);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "不良详情")
    @PreAuthorize("@ps.hasPermi('pm:quality:list')")
    @GetMapping("/{id}")
    public R<PmmQualityDefect> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "不良登记")
    @PreAuthorize("@ps.hasPermi('pm:quality:add')")
    @OperLog(title = "质量不良", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody PmmQualityDefect entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("登记失败") : R.ok(id);
    }

    @Operation(summary = "不良处理 (纠正/预防/处置)")
    @PreAuthorize("@ps.hasPermi('pm:quality:handle')")
    @OperLog(title = "质量不良", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody PmmQualityDefect entity) {
        return service.update(entity) ? R.ok(1) : R.fail("处理失败");
    }

    @Operation(summary = "不良记录导出(预留)")
    @PreAuthorize("@ps.hasPermi('pm:quality:export')")
    @GetMapping("/export")
    public R<List<PmmQualityDefect>> export(@RequestParam(required = false) Long workOrderId,
                                            @RequestParam(required = false) String defectCode,
                                            @RequestParam(required = false) String defectLevel,
                                            @RequestParam(required = false) String checkType,
                                            @RequestParam(required = false) String disposition,
                                            @RequestParam(required = false) String processName) {
        // 预留: 先直接取前 1000 条返回, 后面加 EasyExcel 真正导出
        IPage<PmmQualityDefect> page = service.selectPage(1, 1000, workOrderId, defectCode, defectLevel,
                checkType, disposition, processName, null);
        return R.ok(page.getRecords());
    }

    @Operation(summary = "按工单获取不良列表")
    @PreAuthorize("@ps.hasPermi('pm:quality:list')")
    @GetMapping("/listByWorkOrder")
    public R<List<PmmQualityDefect>> listByWorkOrder(@RequestParam Long workOrderId) {
        return R.ok(service.listByWorkOrder(workOrderId));
    }
}
