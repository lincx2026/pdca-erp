package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.PmmDefectCode;
import com.pcba.erp.pmm.service.IPmmDefectCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "不良代码字典")
@RestController
@RequestMapping("/pm/defect-code")
@RequiredArgsConstructor
public class PmmDefectCodeController {

    private final IPmmDefectCodeService service;

    @Operation(summary = "不良代码分页")
    @PreAuthorize("@ps.hasPermi('pm:quality:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String defectCode,
                                 @RequestParam(required = false) String defectName,
                                 @RequestParam(required = false) String defectCategory,
                                 @RequestParam(required = false) Integer status) {
        IPage<PmmDefectCode> page = service.selectPage(pageNum, pageSize, defectCode, defectName, defectCategory, status);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "不良代码详情")
    @PreAuthorize("@ps.hasPermi('pm:quality:list')")
    @GetMapping("/{id}")
    public R<PmmDefectCode> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增不良代码")
    @PreAuthorize("@ps.hasPermi('pm:quality:add')")
    @OperLog(title = "不良代码字典", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody PmmDefectCode entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改不良代码")
    @PreAuthorize("@ps.hasPermi('pm:quality:handle')")
    @OperLog(title = "不良代码字典", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody PmmDefectCode entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除不良代码")
    @PreAuthorize("@ps.hasPermi('pm:quality:handle')")
    @OperLog(title = "不良代码字典", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "启用不良代码")
    @PreAuthorize("@ps.hasPermi('pm:quality:handle')")
    @OperLog(title = "不良代码字典", businessType = 2)
    @PutMapping("/enable/{id}")
    public R<Integer> enable(@PathVariable Long id) { return service.changeStatus(id, 1) ? R.ok(1) : R.fail("启用失败"); }

    @Operation(summary = "停用不良代码")
    @PreAuthorize("@ps.hasPermi('pm:quality:handle')")
    @OperLog(title = "不良代码字典", businessType = 2)
    @PutMapping("/disable/{id}")
    public R<Integer> disable(@PathVariable Long id) { return service.changeStatus(id, 0) ? R.ok(1) : R.fail("停用失败"); }

    @Operation(summary = "不良代码下拉列表 (启用)")
    @PreAuthorize("@ps.hasPermi('pm:quality:list')")
    @GetMapping("/options")
    public R<List<PmmDefectCode>> options() { return R.ok(service.options()); }
}
