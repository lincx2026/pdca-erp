package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.PmmProcessTask;
import com.pcba.erp.pmm.service.IPmmProcessTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "工序任务管理")
@RestController
@RequestMapping("/pm/process-task")
@RequiredArgsConstructor
public class PmmProcessTaskController {

    private final IPmmProcessTaskService service;

    @Operation(summary = "工序任务分页")
    @PreAuthorize("@ps.hasPermi('pm:processtask:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) Long workOrderId,
                                 @RequestParam(required = false) String processName,
                                 @RequestParam(required = false) String workCenter,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) Long assignedUserId) {
        IPage<PmmProcessTask> page = service.selectPage(pageNum, pageSize, workOrderId, processName, workCenter, status, assignedUserId);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "工序任务详情")
    @PreAuthorize("@ps.hasPermi('pm:processtask:list')")
    @GetMapping("/{id}")
    public R<PmmProcessTask> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "派发工序任务")
    @PreAuthorize("@ps.hasPermi('pm:processtask:dispatch')")
    @OperLog(title = "工序任务", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody PmmProcessTask entity) {
        // 派发即直接置为已派发状态 10, 允许前端显式覆盖
        if (entity.getStatus() == null) entity.setStatus(10);
        Long id = service.save(entity);
        return id == null ? R.fail("派发失败") : R.ok(id);
    }

    @Operation(summary = "修改工序任务")
    @PreAuthorize("@ps.hasPermi('pm:processtask:edit')")
    @OperLog(title = "工序任务", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody PmmProcessTask entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除工序任务")
    @PreAuthorize("@ps.hasPermi('pm:processtask:remove')")
    @OperLog(title = "工序任务", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "工序开工")
    @PreAuthorize("@ps.hasPermi('pm:processtask:start')")
    @OperLog(title = "工序任务", businessType = 2)
    @PutMapping("/start/{id}")
    public R<Integer> start(@PathVariable Long id) { return service.changeStatus(id, 20) ? R.ok(1) : R.fail("开工失败"); }

    @Operation(summary = "工序完工")
    @PreAuthorize("@ps.hasPermi('pm:processtask:finish')")
    @OperLog(title = "工序任务", businessType = 2)
    @PutMapping("/finish/{id}")
    public R<Integer> finish(@PathVariable Long id) { return service.changeStatus(id, 40) ? R.ok(1) : R.fail("完工失败"); }

    @Operation(summary = "工序流转到下工序")
    @PreAuthorize("@ps.hasPermi('pm:processtask:transfer')")
    @OperLog(title = "工序任务", businessType = 2)
    @PutMapping("/transfer/{id}")
    public R<Integer> transfer(@PathVariable Long id) { return service.changeStatus(id, 50) ? R.ok(1) : R.fail("流转失败"); }

    @Operation(summary = "按工单获取工序任务列表")
    @PreAuthorize("@ps.hasPermi('pm:processtask:list')")
    @GetMapping("/listByWorkOrder")
    public R<List<PmmProcessTask>> listByWorkOrder(@RequestParam Long workOrderId) {
        return R.ok(service.listByWorkOrder(workOrderId));
    }
}
