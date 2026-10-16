package com.pcba.erp.sal.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.sal.entity.SalArReceivableLine;
import com.pcba.erp.sal.service.ISalArReceivableLineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "应收账-应收单明细行")
@RestController
@RequestMapping("/sal/ar-receivable-line")
@RequiredArgsConstructor
public class SalArReceivableLineController {

    private final ISalArReceivableLineService service;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('sal:arreceivableline:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) Long receivableId, @RequestParam(required = false) String materialCode, @RequestParam(required = false) String materialName) {
        IPage<SalArReceivableLine> page = service.selectPage(pageNum, pageSize, receivableId, materialCode, materialName);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('sal:arreceivableline:list')")
    @GetMapping("/{id}")
    public R<SalArReceivableLine> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('sal:arreceivableline:add')")
    @OperLog(title = "SalArReceivableLine", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody SalArReceivableLine entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('sal:arreceivableline:edit')")
    @OperLog(title = "SalArReceivableLine", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody SalArReceivableLine entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('sal:arreceivableline:remove')")
    @OperLog(title = "SalArReceivableLine", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }
}
