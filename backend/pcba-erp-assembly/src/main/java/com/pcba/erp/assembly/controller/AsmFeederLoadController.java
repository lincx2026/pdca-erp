package com.pcba.erp.assembly.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.assembly.entity.AsmFeederLoad;
import com.pcba.erp.assembly.service.IAsmFeederLoadService;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Feeder 上料 Controller
 */
@Tag(name = "Feeder上料管理")
@RestController
@RequestMapping("/asm/feeder-load")
@RequiredArgsConstructor
public class AsmFeederLoadController {

    private final IAsmFeederLoadService feederLoadService;

    @Operation(summary = "Feeder上料分页查询")
    @PreAuthorize("@ps.hasPermi('asm:feeder:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String equipmentCode,
            @RequestParam(required = false) String feederNo,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Integer status) {
        IPage<AsmFeederLoad> page = feederLoadService.selectPage(pageNum, pageSize, equipmentCode, feederNo, materialId, status);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "Feeder上料详情")
    @PreAuthorize("@ps.hasPermi('asm:feeder:list')")
    @GetMapping("/{id}")
    public R<AsmFeederLoad> get(@PathVariable Long id) {
        return R.ok(feederLoadService.getDetail(id));
    }

    @Operation(summary = "Feeder上料下拉列表")
    @PreAuthorize("@ps.hasPermi('asm:feeder:list')")
    @GetMapping("/options")
    public R<List<AsmFeederLoad>> options() {
        return R.ok(feederLoadService.options());
    }

    @Operation(summary = "新增Feeder上料")
    @PreAuthorize("@ps.hasPermi('asm:feeder:add')")
    @OperLog(title = "Feeder上料", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody AsmFeederLoad entity) {
        return R.ok(feederLoadService.save(entity));
    }

    @Operation(summary = "修改Feeder上料")
    @PreAuthorize("@ps.hasPermi('asm:feeder:edit')")
    @OperLog(title = "Feeder上料", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody AsmFeederLoad entity) {
        return feederLoadService.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除Feeder上料")
    @PreAuthorize("@ps.hasPermi('asm:feeder:remove')")
    @OperLog(title = "Feeder上料", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return feederLoadService.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    // ============== 业务动作 ==============

    @Operation(summary = "Feeder上料")
    @PreAuthorize("@ps.hasPermi('asm:feeder:load')")
    @OperLog(title = "Feeder上料", businessType = 4)
    @PostMapping("/load")
    public R<Integer> loadFeeder(@Validated @RequestBody AsmFeederLoad entity) {
        return feederLoadService.loadFeeder(entity) ? R.ok(1) : R.fail("上料失败");
    }

    @Operation(summary = "Feeder下料")
    @PreAuthorize("@ps.hasPermi('asm:feeder:unload')")
    @OperLog(title = "Feeder上料", businessType = 4)
    @PutMapping("/unload/{id}")
    public R<Integer> unloadFeeder(@PathVariable Long id) {
        return feederLoadService.unloadFeeder(id) ? R.ok(1) : R.fail("下料失败");
    }

    @Operation(summary = "Feeder报废")
    @PreAuthorize("@ps.hasPermi('asm:feeder:scrap')")
    @OperLog(title = "Feeder上料", businessType = 4)
    @PutMapping("/scrap/{id}")
    public R<Integer> scrapFeeder(@PathVariable Long id) {
        return feederLoadService.scrapFeeder(id) ? R.ok(1) : R.fail("报废失败");
    }
}
