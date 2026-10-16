package com.pcba.erp.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.system.entity.SysOperLog;
import com.pcba.erp.system.service.ISysOperLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "操作日志")
@RestController
@RequestMapping("/monitor/operlog")
@RequiredArgsConstructor
public class SysOperlogController {

    private final ISysOperLogService operLogService;

    @Operation(summary = "分页操作日志")
    @PreAuthorize("@ps.hasPermi('monitor:operlog:list')")
    @GetMapping("/list")
    public TableDataInfo<SysOperLog> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer businessType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String operName,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime) {
        IPage<SysOperLog> p = operLogService.selectPage(pageNum, pageSize, title, businessType, status, operName, beginTime, endTime);
        return TableDataInfo.of(p.getRecords(), p.getTotal());
    }

    @Operation(summary = "操作日志详情")
    @PreAuthorize("@ps.hasPermi('monitor:operlog:query')")
    @GetMapping("/{id}")
    public R<SysOperLog> get(@PathVariable Long id) {
        return R.ok(operLogService.getById(id));
    }

    @Operation(summary = "批量删除日志")
    @PreAuthorize("@ps.hasPermi('monitor:operlog:remove')")
    @OperLog(title = "操作日志", businessType = 3)
    @DeleteMapping("/{ids}")
    public R<Integer> remove(@PathVariable List<Long> ids) {
        boolean ok = operLogService.removeByIds(ids);
        return ok ? R.ok(ids.size()) : R.fail("删除失败");
    }

    @Operation(summary = "清空所有日志")
    @PreAuthorize("@ps.hasPermi('monitor:operlog:remove')")
    @OperLog(title = "操作日志", businessType = 9)
    @DeleteMapping("/clean")
    public R<Void> clean() {
        operLogService.clean();
        return R.ok();
    }
}
