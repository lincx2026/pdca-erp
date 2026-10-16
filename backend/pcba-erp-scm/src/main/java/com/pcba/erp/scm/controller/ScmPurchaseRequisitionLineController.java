package com.pcba.erp.scm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.scm.entity.ScmPurchaseRequisitionLine;
import com.pcba.erp.scm.service.IScmPurchaseRequisitionLineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "采购-requisition-line")
@RestController
@RequestMapping("/scm/requisition-line")
@RequiredArgsConstructor
public class ScmPurchaseRequisitionLineController {

    private final IScmPurchaseRequisitionLineService service;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('scm:requisitionline:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) Long requisitionId, @RequestParam(required = false) String materialCode, @RequestParam(required = false) String materialName) {
        IPage<ScmPurchaseRequisitionLine> page = service.selectPage(pageNum, pageSize, requisitionId, materialCode, materialName);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('scm:requisitionline:list')")
    @GetMapping("/{id}")
    public R<ScmPurchaseRequisitionLine> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('scm:requisitionline:add')")
    @OperLog(title = "ScmPurchaseRequisitionLine", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody ScmPurchaseRequisitionLine entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('scm:requisitionline:edit')")
    @OperLog(title = "ScmPurchaseRequisitionLine", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody ScmPurchaseRequisitionLine entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('scm:requisitionline:remove')")
    @OperLog(title = "ScmPurchaseRequisitionLine", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }


    @Operation(summary = "下拉选项")
    @PreAuthorize("@ps.hasPermi('scm:requisitionline:list')")
    @GetMapping("/options")
    public R<List<ScmPurchaseRequisitionLine>> options() { return R.ok(service.options()); }
}
