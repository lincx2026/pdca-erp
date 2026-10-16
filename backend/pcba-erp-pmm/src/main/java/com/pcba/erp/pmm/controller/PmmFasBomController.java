package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.FasBom;
import com.pcba.erp.pmm.service.IPmmFasBomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "后道组装BOM管理")
@RestController
@RequestMapping("/fas/bom")
@RequiredArgsConstructor
public class PmmFasBomController {

    private final IPmmFasBomService service;

    @Operation(summary = "BOM分页查询")
    @PreAuthorize("@ps.hasPermi('pm:fas:bom:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) Long productId,
                                 @RequestParam(required = false) String componentName,
                                 @RequestParam(required = false) String componentType) {
        IPage<FasBom> page = service.selectPage(pageNum, pageSize, productId, componentName, componentType);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "BOM详情")
    @PreAuthorize("@ps.hasPermi('pm:fas:bom:query')")
    @GetMapping("/{id}")
    public R<FasBom> get(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "按成品查询BOM清单")
    @PreAuthorize("@ps.hasPermi('pm:fas:bom:list')")
    @GetMapping("/product/{productId}")
    public R<List<FasBom>> listByProduct(@PathVariable Long productId) {
        return R.ok(service.listByProduct(productId));
    }

    @Operation(summary = "新增BOM")
    @PreAuthorize("@ps.hasPermi('pm:fas:bom:add')")
    @OperLog(title = "后道组装BOM", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody FasBom entity) {
        Long id = service.create(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "编辑BOM")
    @PreAuthorize("@ps.hasPermi('pm:fas:bom:edit')")
    @OperLog(title = "后道组装BOM", businessType = 2)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody FasBom entity) {
        service.update(entity);
        return R.ok();
    }

    @Operation(summary = "删除BOM")
    @PreAuthorize("@ps.hasPermi('pm:fas:bom:remove')")
    @OperLog(title = "后道组装BOM", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return R.ok();
    }
}
