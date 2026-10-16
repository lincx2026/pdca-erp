package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.FasStep;
import com.pcba.erp.pmm.service.IPmmFasStepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "后道组装工序管理")
@RestController
@RequestMapping("/fas/step")
@RequiredArgsConstructor
public class PmmFasStepController {

    private final IPmmFasStepService service;

    @Operation(summary = "工序分页查询")
    @PreAuthorize("@ps.hasPermi('pm:fas:step:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) Long orderId,
                                 @RequestParam(required = false) String stepName,
                                 @RequestParam(required = false) Integer status) {
        IPage<FasStep> page = service.selectPage(pageNum, pageSize, orderId, stepName, status);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "工序详情")
    @PreAuthorize("@ps.hasPermi('pm:fas:step:query')")
    @GetMapping("/{id}")
    public R<FasStep> get(@PathVariable Long id) {
        return R.ok();
    }

    @Operation(summary = "按工单查询工序列表")
    @PreAuthorize("@ps.hasPermi('pm:fas:step:list')")
    @GetMapping("/order/{orderId}")
    public R<List<FasStep>> listByOrder(@PathVariable Long orderId) {
        return R.ok(service.listByOrder(orderId));
    }

    @Operation(summary = "新增工序")
    @PreAuthorize("@ps.hasPermi('pm:fas:step:add')")
    @OperLog(title = "后道组装工序", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody FasStep entity) {
        Long id = service.create(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "编辑工序")
    @PreAuthorize("@ps.hasPermi('pm:fas:step:edit')")
    @OperLog(title = "后道组装工序", businessType = 2)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody FasStep entity) {
        service.update(entity);
        return R.ok();
    }

    @Operation(summary = "删除工序")
    @PreAuthorize("@ps.hasPermi('pm:fas:step:remove')")
    @OperLog(title = "后道组装工序", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return R.ok();
    }

    @Operation(summary = "开始工序")
    @PreAuthorize("@ps.hasPermi('pm:fas:step:edit')")
    @PostMapping("/{stepId}/start")
    public R<Void> start(@PathVariable Long stepId, @RequestParam(required = false) String operator) {
        service.startStep(stepId, operator);
        return R.ok();
    }

    @Operation(summary = "完成工序")
    @PreAuthorize("@ps.hasPermi('pm:fas:step:edit')")
    @PostMapping("/{stepId}/complete")
    public R<Void> complete(@PathVariable Long stepId,
                            @RequestParam(required = false) Integer qtyPass,
                            @RequestParam(required = false) Integer qtyNg,
                            @RequestParam(required = false) String operator) {
        service.completeStep(stepId, qtyPass, qtyNg, operator);
        return R.ok();
    }
}
