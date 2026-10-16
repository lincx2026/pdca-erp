package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.PmmJig;
import com.pcba.erp.pmm.entity.PmmJigLog;
import com.pcba.erp.pmm.service.IPmmJigLogService;
import com.pcba.erp.pmm.service.IPmmJigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工装治具 Controller
 * 权限前缀: pm:jig:*  (与 sys_menu perms 字段对齐 menu_id=820~827)
 */
@Tag(name = "工装治具")
@RestController
@RequestMapping("/pm/jig")
@RequiredArgsConstructor
public class PmmJigController {

    private final IPmmJigService    jigService;
    private final IPmmJigLogService logService;

    // =========================================================
    //                      工装档案 CRUD
    // =========================================================

    @Operation(summary = "工装档案分页查询")
    @PreAuthorize("@ps.hasPermi('pm:jig:list') or @ps.hasPermi('pm:jig:query')")
    @GetMapping("/list")
    public R<TableDataInfo> list(
            @RequestParam(defaultValue = "1")  int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String jigNo,
            @RequestParam(required = false) String jigName,
            @RequestParam(required = false) String jigType,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keeper,
            @RequestParam(required = false) String warehouse,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String borrowerName) {
        IPage<PmmJig> page = jigService.selectPage(pageNum, pageSize,
                jigNo, jigName, jigType, productCode, productName, status,
                keeper, warehouse, location, borrowerName);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "工装详情")
    @PreAuthorize("@ps.hasPermi('pm:jig:query')")
    @GetMapping("/{id}")
    public R<PmmJig> get(@PathVariable("id") Long id) {
        return R.ok(jigService.getDetail(id));
    }

    @Operation(summary = "工装下拉字典(按机种筛选)")
    @PreAuthorize("@ps.hasPermi('pm:jig:list') or @ps.hasPermi('pm:jig:query')")
    @GetMapping("/options")
    public R<List<PmmJig>> options(@RequestParam(required = false) String productCode) {
        return R.ok(jigService.options(productCode));
    }

    @Operation(summary = "新增工装")
    @PreAuthorize("@ps.hasPermi('pm:jig:add')")
    @OperLog(title = "工装治具-新增", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody PmmJig entity) {
        return R.ok(jigService.save(entity));
    }

    @Operation(summary = "修改工装")
    @PreAuthorize("@ps.hasPermi('pm:jig:edit')")
    @OperLog(title = "工装治具-修改", businessType = 2)
    @PutMapping
    public R<Boolean> edit(@Validated @RequestBody PmmJig entity) {
        return R.ok(jigService.update(entity));
    }

    @Operation(summary = "删除工装 (仅报废状态允许)")
    @PreAuthorize("@ps.hasPermi('pm:jig:remove')")
    @OperLog(title = "工装治具-删除", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Boolean> remove(@PathVariable("id") Long id) {
        return R.ok(jigService.remove(id));
    }

    // =========================================================
    //                   6 大业务动作 (需要 pm:jig:operate 权限)
    // =========================================================

    @Operation(summary = "领用")
    @PreAuthorize("@ps.hasPermi('pm:jig:operate')")
    @OperLog(title = "工装治具-领用", businessType = 4)
    @PostMapping("/{id}/borrow")
    public R<Long> borrow(@PathVariable("id") Long id, @RequestBody PmmJigLog log) {
        return R.ok(jigService.borrow(id, log));
    }

    @Operation(summary = "归还 (含 本次使用次数 usedCountAdd)")
    @PreAuthorize("@ps.hasPermi('pm:jig:operate')")
    @OperLog(title = "工装治具-归还", businessType = 4)
    @PostMapping("/{id}/return")
    public R<Long> returnBack(@PathVariable("id") Long id, @RequestBody PmmJigLog log) {
        return R.ok(jigService.returnBack(id, log));
    }

    @Operation(summary = "校准 (张力/探针/扭力等)")
    @PreAuthorize("@ps.hasPermi('pm:jig:operate')")
    @OperLog(title = "工装治具-校准", businessType = 4)
    @PostMapping("/{id}/calibrate")
    public R<Long> calibrate(@PathVariable("id") Long id, @RequestBody PmmJigLog log) {
        return R.ok(jigService.calibrate(id, log));
    }

    @Operation(summary = "修磨/更换钢片/重置寿命")
    @PreAuthorize("@ps.hasPermi('pm:jig:operate')")
    @OperLog(title = "工装治具-修磨", businessType = 4)
    @PostMapping("/{id}/resharpen")
    public R<Long> resharpen(@PathVariable("id") Long id, @RequestBody PmmJigLog log) {
        return R.ok(jigService.resharpen(id, log));
    }

    @Operation(summary = "维修")
    @PreAuthorize("@ps.hasPermi('pm:jig:operate')")
    @OperLog(title = "工装治具-维修", businessType = 2)
    @PostMapping("/{id}/repair")
    public R<Long> repair(@PathVariable("id") Long id, @RequestBody PmmJigLog log) {
        return R.ok(jigService.repair(id, log));
    }

    @Operation(summary = "报废 (扣减总数/套数)")
    @PreAuthorize("@ps.hasPermi('pm:jig:scrap')")
    @OperLog(title = "工装治具-报废", businessType = 3)
    @PostMapping("/{id}/scrap")
    public R<Long> scrap(@PathVariable("id") Long id, @RequestBody(required = false) PmmJigLog log) {
        return R.ok(jigService.scrap(id, log));
    }

    // =========================================================
    //                      动作流水日志
    // =========================================================

    @Operation(summary = "动作日志分页查询")
    @PreAuthorize("@ps.hasPermi('pm:jig:list') or @ps.hasPermi('pm:jig:query')")
    @GetMapping("/log/list")
    public R<TableDataInfo> logList(
            @RequestParam(defaultValue = "1")  int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long jigId,
            @RequestParam(required = false) String jigNo,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String eventTitle,
            @RequestParam(required = false) String eventResult,
            @RequestParam(required = false) String borrowerName,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        IPage<PmmJigLog> page = logService.selectPage(pageNum, pageSize,
                jigId, jigNo, eventType, eventTitle, eventResult, borrowerName, startTime, endTime);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "日志详情")
    @PreAuthorize("@ps.hasPermi('pm:jig:query')")
    @GetMapping("/log/{id}")
    public R<PmmJigLog> logGet(@PathVariable("id") Long id) {
        return R.ok(logService.getDetail(id));
    }

    @Operation(summary = "删除日志")
    @PreAuthorize("@ps.hasPermi('pm:jig:remove')")
    @OperLog(title = "工装治具-日志删除", businessType = 3)
    @DeleteMapping("/log/{id}")
    public R<Boolean> logRemove(@PathVariable("id") Long id) {
        return R.ok(logService.remove(id));
    }
}
