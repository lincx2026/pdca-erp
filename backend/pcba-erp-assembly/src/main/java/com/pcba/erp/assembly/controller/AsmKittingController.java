package com.pcba.erp.assembly.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.assembly.entity.AsmKitting;
import com.pcba.erp.assembly.service.IAsmKittingService;
import com.pcba.erp.assembly.service.IAsmKittingLineService;
import com.pcba.erp.assembly.entity.AsmKittingLine;
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
 * 配料单 Controller
 */
@Tag(name = "配料单管理")
@RestController
@RequestMapping("/asm/kitting")
@RequiredArgsConstructor
public class AsmKittingController {

    private final IAsmKittingService kittingService;
    private final IAsmKittingLineService lineService;

    @PreAuthorize("@ps.hasPermi('asm:kitting:list')")
    @GetMapping("/lines")
    public R<List<AsmKittingLine>> lines(@RequestParam Long kittingId) {
        return R.ok(lineService.listByKittingId(kittingId));
    }

    @Operation(summary = "配料单分页查询")
    @PreAuthorize("@ps.hasPermi('asm:kitting:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String kittingNo,
            @RequestParam(required = false) Long workOrderId,
            @RequestParam(required = false) Integer status) {
        IPage<AsmKitting> page = kittingService.selectPage(pageNum, pageSize, kittingNo, workOrderId, status);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "配料单详情")
    @PreAuthorize("@ps.hasPermi('asm:kitting:list')")
    @GetMapping("/{id}")
    public R<AsmKitting> get(@PathVariable Long id) {
        return R.ok(kittingService.getDetail(id));
    }

    @Operation(summary = "配料单下拉列表")
    @PreAuthorize("@ps.hasPermi('asm:kitting:list')")
    @GetMapping("/options")
    public R<List<AsmKitting>> options() {
        return R.ok(kittingService.options());
    }

    @Operation(summary = "生成配料单号")
    @PreAuthorize("@ps.hasPermi('asm:kitting:add')")
    @GetMapping("/next-code")
    public R<String> nextCode() {
        return R.ok(kittingService.nextKittingNo());
    }

    @Operation(summary = "新增配料单")
    @PreAuthorize("@ps.hasPermi('asm:kitting:add')")
    @OperLog(title = "配料单", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody AsmKitting entity) {
        return R.ok(kittingService.save(entity));
    }

    @Operation(summary = "修改配料单")
    @PreAuthorize("@ps.hasPermi('asm:kitting:edit')")
    @OperLog(title = "配料单", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody AsmKitting entity) {
        return kittingService.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除配料单")
    @PreAuthorize("@ps.hasPermi('asm:kitting:remove')")
    @OperLog(title = "配料单", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return kittingService.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    // ============== 状态流转 ==============

    @Operation(summary = "开始配料")
    @PreAuthorize("@ps.hasPermi('asm:kitting:edit')")
    @OperLog(title = "配料单", businessType = 4)
    @PutMapping("/start/{id}")
    public R<Integer> start(@PathVariable Long id) {
        return kittingService.startKitting(id) ? R.ok(1) : R.fail("操作失败");
    }

    @Operation(summary = "完成配料")
    @PreAuthorize("@ps.hasPermi('asm:kitting:edit')")
    @OperLog(title = "配料单", businessType = 4)
    @PutMapping("/finish/{id}")
    public R<Integer> finish(@PathVariable Long id) {
        return kittingService.finishKitting(id) ? R.ok(1) : R.fail("操作失败");
    }

    @Operation(summary = "发料确认")
    @PreAuthorize("@ps.hasPermi('asm:kitting:edit')")
    @OperLog(title = "配料单", businessType = 4)
    @PutMapping("/issue/{id}")
    public R<Integer> issue(@PathVariable Long id) {
        return kittingService.issueMaterial(id) ? R.ok(1) : R.fail("操作失败");
    }

    @Operation(summary = "取消配料单")
    @PreAuthorize("@ps.hasPermi('asm:kitting:edit')")
    @OperLog(title = "配料单", businessType = 4)
    @PutMapping("/cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id) {
        return kittingService.cancelKitting(id) ? R.ok(1) : R.fail("操作失败");
    }
}
