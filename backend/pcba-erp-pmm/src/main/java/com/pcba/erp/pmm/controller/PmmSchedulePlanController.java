package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.PmmSchedulePlan;
import com.pcba.erp.pmm.service.IPmmSchedulePlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "排产计划管理")
@RestController
@RequestMapping("/pm/schedule")
@RequiredArgsConstructor
public class PmmSchedulePlanController {

    private final IPmmSchedulePlanService service;

    @Operation(summary = "排产计划分页")
    @PreAuthorize("@ps.hasPermi('pm:schedule:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduleDate,
                                 @RequestParam(required = false) String shift,
                                 @RequestParam(required = false) Long workOrderId,
                                 @RequestParam(required = false) String workCenter,
                                 @RequestParam(required = false) String machineCode) {
        IPage<PmmSchedulePlan> page = service.selectPage(pageNum, pageSize, scheduleDate, shift, workOrderId, workCenter, machineCode);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "排产计划详情")
    @PreAuthorize("@ps.hasPermi('pm:schedule:list')")
    @GetMapping("/{id}")
    public R<PmmSchedulePlan> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "保存排产计划 (新增/修改 统一入口)")
    @PreAuthorize("@ps.hasPermi('pm:schedule:save')")
    @OperLog(title = "排产计划", businessType = 2)
    @PostMapping("/save")
    public R<Long> save(@Validated @RequestBody PmmSchedulePlan entity) {
        Long id;
        if (entity.getPlanId() == null) {
            id = service.save(entity);
        } else {
            service.update(entity);
            id = entity.getPlanId();
        }
        return id == null ? R.fail("保存失败") : R.ok(id);
    }

    @Operation(summary = "删除排产计划")
    @PreAuthorize("@ps.hasPermi('pm:schedule:save')")
    @OperLog(title = "排产计划", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "按日期获取全天排产 (甘特视图前端用)")
    @PreAuthorize("@ps.hasPermi('pm:schedule:list')")
    @GetMapping("/listByDate")
    public R<List<PmmSchedulePlan>> listByDate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(service.listByDate(date));
    }
}
