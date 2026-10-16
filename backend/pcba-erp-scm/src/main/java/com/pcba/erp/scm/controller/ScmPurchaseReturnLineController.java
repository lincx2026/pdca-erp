package com.pcba.erp.scm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.scm.entity.ScmPurchaseReturnLine;
import com.pcba.erp.scm.service.IScmPurchaseReturnLineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "采购-return-line")
@RestController
@RequestMapping("/scm/return-line")
@RequiredArgsConstructor
public class ScmPurchaseReturnLineController {

    private final IScmPurchaseReturnLineService service;

    @Operation(summary = "分页查询(按退货单returnId)")
    @PreAuthorize("@ps.hasPermi('scm:return:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "500") int pageSize,
                                 @RequestParam(required = false) Long returnId,
                                 @RequestParam(required = false) String materialCode,
                                 @RequestParam(required = false) String materialName) {
        IPage<ScmPurchaseReturnLine> page = service.selectPage(pageNum, pageSize, returnId, materialCode, materialName);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('scm:return:list')")
    @GetMapping("/{id}")
    public R<ScmPurchaseReturnLine> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('scm:return:add')")
    @OperLog(title = "ScmPurchaseReturnLine", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody ScmPurchaseReturnLine entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('scm:return:edit')")
    @OperLog(title = "ScmPurchaseReturnLine", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody ScmPurchaseReturnLine entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('scm:return:remove')")
    @OperLog(title = "ScmPurchaseReturnLine", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "下拉选项")
    @PreAuthorize("@ps.hasPermi('scm:return:list')")
    @GetMapping("/options")
    public R<List<ScmPurchaseReturnLine>> options() { return R.ok(service.options()); }
}
