package com.pcba.erp.sal.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.sal.entity.SalSalesReturnLine;
import com.pcba.erp.sal.service.ISalSalesReturnLineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "销售-sales-return-line")
@RestController
@RequestMapping("/sal/return-line")
@RequiredArgsConstructor
public class SalSalesReturnLineController {

    private final ISalSalesReturnLineService service;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('sal:returnline:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) Long returnId, @RequestParam(required = false) String materialCode, @RequestParam(required = false) String materialName) {
        IPage<SalSalesReturnLine> page = service.selectPage(pageNum, pageSize, returnId, materialCode, materialName);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('sal:returnline:list')")
    @GetMapping("/{id}")
    public R<SalSalesReturnLine> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('sal:returnline:add')")
    @OperLog(title = "SalSalesReturnLine", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody SalSalesReturnLine entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('sal:returnline:edit')")
    @OperLog(title = "SalSalesReturnLine", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody SalSalesReturnLine entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('sal:returnline:remove')")
    @OperLog(title = "SalSalesReturnLine", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }
}
