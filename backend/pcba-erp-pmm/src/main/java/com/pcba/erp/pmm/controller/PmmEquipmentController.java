package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.PmmEquipment;
import com.pcba.erp.pmm.entity.PmmEquipmentLog;
import com.pcba.erp.pmm.service.IPmmEquipmentLogService;
import com.pcba.erp.pmm.service.IPmmEquipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备管理 Controller
 * 权限前缀: pm:equipment:*  (与 sys_menu 的 perms 字段对齐 menu_id=810~815)
 */
@Tag(name = "设备管理")
@RestController
@RequestMapping("/pm/equipment")
@RequiredArgsConstructor
public class PmmEquipmentController {

    private final IPmmEquipmentService    equipService;
    private final IPmmEquipmentLogService logService;

    // =========================================================
    //                        设备档案 CRUD
    // =========================================================

    @Operation(summary = "设备档案分页查询")
    @PreAuthorize("@ps.hasPermi('pm:equipment:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(
            @RequestParam(defaultValue = "1")  int    pageNum,
            @RequestParam(defaultValue = "20") int    pageSize,
            @RequestParam(required = false)    String equipmentNo,
            @RequestParam(required = false)    String equipmentName,
            @RequestParam(required = false)    String equipmentType,
            @RequestParam(required = false)    Integer status,
            @RequestParam(required = false)    Integer equipLevel,
            @RequestParam(required = false)    String workshop,
            @RequestParam(required = false)    String workCenter) {
        IPage<PmmEquipment> page = equipService.selectPage(pageNum, pageSize,
                equipmentNo, equipmentName, equipmentType, status, equipLevel, workshop, workCenter);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "设备详情")
    @PreAuthorize("@ps.hasPermi('pm:equipment:query')")
    @GetMapping("/{id}")
    public R<PmmEquipment> get(@PathVariable Long id) {
        return R.ok(equipService.getDetail(id));
    }

    @Operation(summary = "设备下拉列表 (ASM/SN/排产下拉用, 过滤报废)")
    @PreAuthorize("@ps.hasPermi('pm:equipment:list')")
    @GetMapping("/options")
    public R<List<PmmEquipment>> options() {
        return R.ok(equipService.options());
    }

    @Operation(summary = "新增设备")
    @PreAuthorize("@ps.hasPermi('pm:equipment:add')")
    @OperLog(title = "设备管理", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody PmmEquipment entity) {
        return R.ok(equipService.save(entity));
    }

    @Operation(summary = "修改设备")
    @PreAuthorize("@ps.hasPermi('pm:equipment:edit')")
    @OperLog(title = "设备管理", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody PmmEquipment entity) {
        return equipService.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除设备")
    @PreAuthorize("@ps.hasPermi('pm:equipment:remove')")
    @OperLog(title = "设备管理", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return equipService.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    // =========================================================
    //                       设备事件日志
    // =========================================================

    @Operation(summary = "设备事件日志分页(可按equipment_id/设备编号过滤)")
    @PreAuthorize("@ps.hasPermi('pm:equipment:log')")
    @GetMapping("/log/list")
    public R<TableDataInfo> logList(
            @RequestParam(defaultValue = "1")  int    pageNum,
            @RequestParam(defaultValue = "20") int    pageSize,
            @RequestParam(required = false)    Long   equipmentId,
            @RequestParam(required = false)    String equipmentNo,
            @RequestParam(required = false)    String eventType,
            @RequestParam(required = false)    String eventResult) {
        IPage<PmmEquipmentLog> page = logService.selectPage(pageNum, pageSize, equipmentId, equipmentNo, eventType, eventResult);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "日志详情")
    @PreAuthorize("@ps.hasPermi('pm:equipment:log')")
    @GetMapping("/log/{id}")
    public R<PmmEquipmentLog> logGet(@PathVariable("id") Long logId) {
        return R.ok(logService.getDetail(logId));
    }

    @Operation(summary = "新增设备事件日志(状态变更自动同步主表状态, 保养/校准自动重算下次日期)")
    @PreAuthorize("@ps.hasPermi('pm:equipment:log')")
    @OperLog(title = "设备事件日志", businessType = 1)
    @PostMapping("/log")
    public R<Long> logAdd(@Validated @RequestBody PmmEquipmentLog entity) {
        return R.ok(logService.save(entity));
    }

    @Operation(summary = "删除事件日志")
    @PreAuthorize("@ps.hasPermi('pm:equipment:remove')")
    @OperLog(title = "设备事件日志", businessType = 3)
    @DeleteMapping("/log/{id}")
    public R<Integer> logRemove(@PathVariable("id") Long logId) {
        return logService.remove(logId) ? R.ok(1) : R.fail("删除失败");
    }
}
