package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.PmmWorkReport;
import com.pcba.erp.pmm.service.IPmmWorkReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "生产报工管理")
@RestController
@RequestMapping("/pm/report")
@RequiredArgsConstructor
public class PmmWorkReportController {

    private final IPmmWorkReportService service;

    @Operation(summary = "报工记录分页")
    @PreAuthorize("@ps.hasPermi('pm:report:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String reportNo,
                                 @RequestParam(required = false) Long workOrderId,
                                 @RequestParam(required = false) Long taskId,
                                 @RequestParam(required = false) Long operatorId,
                                 @RequestParam(required = false) String reportType,
                                 @RequestParam(required = false) String shift) {
        IPage<PmmWorkReport> page = service.selectPage(pageNum, pageSize, reportNo, workOrderId, taskId, operatorId, reportType, shift);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "报工详情")
    @PreAuthorize("@ps.hasPermi('pm:report:list')")
    @GetMapping("/{id}")
    public R<PmmWorkReport> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增报工")
    @PreAuthorize("@ps.hasPermi('pm:report:add')")
    @OperLog(title = "生产报工", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody PmmWorkReport entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "撤销报工")
    @PreAuthorize("@ps.hasPermi('pm:report:revoke')")
    @OperLog(title = "生产报工", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> revoke(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("撤销失败");
    }

    @Operation(summary = "QC 审核报工")
    @PreAuthorize("@ps.hasPermi('pm:report:qc')")
    @OperLog(title = "生产报工", businessType = 2)
    @PutMapping("/qc")
    public R<Integer> qc(@RequestBody PmmWorkReport entity) {
        if (entity.getReportId() == null) return R.fail("报工ID不能为空");
        PmmWorkReport patch = new PmmWorkReport();
        patch.setReportId(entity.getReportId());
        patch.setIsQcPass(entity.getIsQcPass());
        patch.setQcBy(entity.getQcBy());
        patch.setQcTime(entity.getQcTime());
        patch.setQcRemark(entity.getQcRemark());
        return service.update(patch) ? R.ok(1) : R.fail("QC失败");
    }

    @Operation(summary = "按工单获取报工列表")
    @PreAuthorize("@ps.hasPermi('pm:report:list')")
    @GetMapping("/listByWorkOrder")
    public R<List<PmmWorkReport>> listByWorkOrder(@RequestParam Long workOrderId) {
        return R.ok(service.listByWorkOrder(workOrderId));
    }
}
