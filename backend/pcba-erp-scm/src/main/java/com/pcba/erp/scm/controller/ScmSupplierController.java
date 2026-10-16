package com.pcba.erp.scm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.scm.entity.ScmSupplier;
import com.pcba.erp.scm.service.IScmSupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "采购-supplier")
@RestController
@RequestMapping("/scm/supplier")
@RequiredArgsConstructor
public class ScmSupplierController {

    private final IScmSupplierService service;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('scm:supplier:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String supplierCode, @RequestParam(required = false) String supplierName, @RequestParam(required = false) String contact, @RequestParam(required = false) String phone, @RequestParam(required = false) String level, @RequestParam(required = false) Integer status) {
        IPage<ScmSupplier> page = service.selectPage(pageNum, pageSize, supplierCode, supplierName, contact, phone, level, status);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('scm:supplier:list')")
    @GetMapping("/{id}")
    public R<ScmSupplier> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('scm:supplier:add')")
    @OperLog(title = "ScmSupplier", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody ScmSupplier entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('scm:supplier:edit')")
    @OperLog(title = "ScmSupplier", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody ScmSupplier entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('scm:supplier:remove')")
    @OperLog(title = "ScmSupplier", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "启用")
    @PreAuthorize("@ps.hasPermi('scm:supplier:edit')")
    @OperLog(title = "启用", businessType = 2)
    @PutMapping("enable/{id}")
    public R<Integer> enable(@PathVariable Long id) { return service.changeStatus(id, 1) ? R.ok(1) : R.fail("启用失败"); }
    @Operation(summary = "停用")
    @PreAuthorize("@ps.hasPermi('scm:supplier:edit')")
    @OperLog(title = "停用", businessType = 2)
    @PutMapping("disable/{id}")
    public R<Integer> disable(@PathVariable Long id) { return service.changeStatus(id, 0) ? R.ok(1) : R.fail("停用失败"); }
    @Operation(summary = "评级")
    @PreAuthorize("@ps.hasPermi('scm:supplier:rate')")
    @OperLog(title = "评级", businessType = 2)
    @PutMapping("rate/{id}")
    public R<Integer> rate(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String,Object> body) {
        // 调用通用 changeStatus 或后续扩展具体业务逻辑
        Integer status = (body != null && body.get("status") != null) ? ((Number) body.get("status")).intValue() : null;
        return service.changeStatus(id, status) ? R.ok(1) : R.fail("评级失败");
    }

    @Operation(summary = "下拉选项")
    @PreAuthorize("@ps.hasPermi('scm:supplier:list')")
    @GetMapping("/options")
    public R<List<ScmSupplier>> options() { return R.ok(service.options()); }

    @Operation(summary = "生成下一个供应商编码")
    @PreAuthorize("@ps.hasPermi('scm:supplier:add')")
    @GetMapping("/next-code")
    public R<String> nextCode() { return R.ok(service.nextSupplierCode()); }
}
