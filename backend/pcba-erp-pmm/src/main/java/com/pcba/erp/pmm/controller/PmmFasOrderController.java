package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.FasOrder;
import com.pcba.erp.pmm.service.IPmmFasOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "后道组装工单管理")
@RestController
@RequestMapping("/fas/order")
@RequiredArgsConstructor
public class PmmFasOrderController {

    private final IPmmFasOrderService service;

    @Operation(summary = "工单分页查询")
    @PreAuthorize("@ps.hasPermi('pm:fas:order:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String orderNo,
                                 @RequestParam(required = false) String productName,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) String lineName) {
        IPage<FasOrder> page = service.selectPage(pageNum, pageSize, orderNo, productName, status, lineName);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "工单详情")
    @PreAuthorize("@ps.hasPermi('pm:fas:order:query')")
    @GetMapping("/{id}")
    public R<FasOrder> get(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "新增工单")
    @PreAuthorize("@ps.hasPermi('pm:fas:order:add')")
    @OperLog(title = "后道组装工单", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody FasOrder entity) {
        Long id = service.create(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "编辑工单")
    @PreAuthorize("@ps.hasPermi('pm:fas:order:edit')")
    @OperLog(title = "后道组装工单", businessType = 2)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody FasOrder entity) {
        service.update(entity);
        return R.ok();
    }

    @Operation(summary = "删除工单")
    @PreAuthorize("@ps.hasPermi('pm:fas:order:remove')")
    @OperLog(title = "后道组装工单", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return R.ok();
    }

    @Operation(summary = "下达工单")
    @PreAuthorize("@ps.hasPermi('pm:fas:order:operate')")
    @OperLog(title = "后道组装工单", businessType = 4)
    @PostMapping("/{id}/release")
    public R<Void> release(@PathVariable Long id) {
        service.release(id);
        return R.ok();
    }

    @Operation(summary = "开工")
    @PreAuthorize("@ps.hasPermi('pm:fas:order:operate')")
    @PostMapping("/{id}/start")
    public R<Void> start(@PathVariable Long id) {
        service.startWork(id);
        return R.ok();
    }

    @Operation(summary = "完工")
    @PreAuthorize("@ps.hasPermi('pm:fas:order:operate')")
    @PostMapping("/{id}/complete")
    public R<Void> complete(@PathVariable Long id) {
        service.complete(id);
        return R.ok();
    }

    @Operation(summary = "生成完工入库单草稿(仓管在仓库管理→成品仓库中审核)")
    @PreAuthorize("@ps.hasPermi('pm:fas:order:operate')")
    @OperLog(title = "后道组装工单", businessType = 4)
    @PostMapping("/{id}/stockIn")
    public R<Void> stockIn(@PathVariable Long id,
                           @RequestParam String warehouse,
                           @RequestParam(required = false) String batchNo,
                           @RequestParam(defaultValue = "0") Integer qty,
                           @RequestParam(required = false) String remark) {
        service.stockIn(id, warehouse, batchNo, qty, remark);
        return R.ok();
    }

    @Operation(summary = "关闭工单")
    @PreAuthorize("@ps.hasPermi('pm:fas:order:operate')")
    @PostMapping("/{id}/close")
    public R<Void> close(@PathVariable Long id) {
        service.close(id);
        return R.ok();
    }
}
