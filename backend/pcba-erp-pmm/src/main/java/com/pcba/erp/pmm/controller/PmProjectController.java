package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.PmProject;
import com.pcba.erp.pmm.service.IPmProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "项目管理")
@RestController
@RequestMapping("/pm/project")
@RequiredArgsConstructor
public class PmProjectController {

    private final IPmProjectService service;

    @Operation(summary = "项目分页列表")
    @PreAuthorize("@ps.hasPermi('pm:project:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String projectCode,
                                 @RequestParam(required = false) String projectName,
                                 @RequestParam(required = false) String customerName,
                                 @RequestParam(required = false) String manager,
                                 @RequestParam(required = false) Long categoryId,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) String priority) {
        IPage<PmProject> page = service.selectPage(pageNum, pageSize,
                projectCode, projectName, customerName, manager, categoryId, status, priority);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "项目详情")
    @PreAuthorize("@ps.hasPermi('pm:project:list')")
    @GetMapping("/{id}")
    public R<PmProject> get(@PathVariable Long id) {
        return R.ok(service.getDetail(id));
    }

    @Operation(summary = "获取下一个项目编号")
    @PreAuthorize("@ps.hasPermi('pm:project:list')")
    @GetMapping("/nextCode")
    public R<String> nextCode() {
        return R.ok(service.nextProjectCode());
    }

    @Operation(summary = "新增项目")
    @PreAuthorize("@ps.hasPermi('pm:project:save')")
    @OperLog(title = "项目管理", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody PmProject entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改项目")
    @PreAuthorize("@ps.hasPermi('pm:project:save')")
    @OperLog(title = "项目管理", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody PmProject entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除项目")
    @PreAuthorize("@ps.hasPermi('pm:project:remove')")
    @OperLog(title = "项目管理", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "项目状态流转 (0待启动->1进行中, 1->2已完成, 0/1->3已取消)")
    @PreAuthorize("@ps.hasPermi('pm:project:save')")
    @OperLog(title = "项目管理", businessType = 2)
    @PutMapping("/status/{id}")
    public R<Integer> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        return service.changeStatus(id, status) ? R.ok(1) : R.fail("状态变更失败");
    }
}
