package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.PmmWorkOrder;
import com.pcba.erp.pmm.service.IPmmWorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "生产工单管理")
@RestController
@RequestMapping("/pm/work-order")
@RequiredArgsConstructor
public class PmmWorkOrderController {

    private final IPmmWorkOrderService service;

    @Operation(summary = "工单分页查询")
    @PreAuthorize("@ps.hasPermi('pm:workorder:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String orderNo,
                                 @RequestParam(required = false) String materialCode,
                                 @RequestParam(required = false) String materialName,
                                 @RequestParam(required = false) String sourceType,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) String workCenter,
                                 @RequestParam(required = false) String saleOrderNo) {
        IPage<PmmWorkOrder> page = service.selectPage(pageNum, pageSize, orderNo, materialCode, materialName, sourceType, status, workCenter, saleOrderNo);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "工单详情")
    @PreAuthorize("@ps.hasPermi('pm:workorder:list')")
    @GetMapping("/{id}")
    public R<PmmWorkOrder> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增工单")
    @PreAuthorize("@ps.hasPermi('pm:workorder:add')")
    @OperLog(title = "生产工单", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody PmmWorkOrder entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改工单")
    @PreAuthorize("@ps.hasPermi('pm:workorder:edit')")
    @OperLog(title = "生产工单", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody PmmWorkOrder entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除工单")
    @PreAuthorize("@ps.hasPermi('pm:workorder:remove')")
    @OperLog(title = "生产工单", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "下发工单")
    @PreAuthorize("@ps.hasPermi('pm:workorder:release')")
    @OperLog(title = "生产工单", businessType = 2)
    @PutMapping("/release/{id}")
    public R<Integer> release(@PathVariable Long id) { return service.changeStatus(id, 10) ? R.ok(1) : R.fail("下发失败"); }

    @Operation(summary = "工单完工")
    @PreAuthorize("@ps.hasPermi('pm:workorder:finish')")
    @OperLog(title = "生产工单", businessType = 2)
    @PutMapping("/finish/{id}")
    public R<Integer> finish(@PathVariable Long id) { return service.changeStatus(id, 40) ? R.ok(1) : R.fail("完工失败"); }

    @Operation(summary = "工单关闭")
    @PreAuthorize("@ps.hasPermi('pm:workorder:close')")
    @OperLog(title = "生产工单", businessType = 2)
    @PutMapping("/close/{id}")
    public R<Integer> close(@PathVariable Long id) { return service.changeStatus(id, 50) ? R.ok(1) : R.fail("关闭失败"); }

    @Operation(summary = "工单取消")
    @PreAuthorize("@ps.hasPermi('pm:workorder:cancel')")
    @OperLog(title = "生产工单", businessType = 2)
    @PutMapping("/cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id) { return service.changeStatus(id, 90) ? R.ok(1) : R.fail("取消失败"); }

    @Operation(summary = "工单下拉列表")
    @PreAuthorize("@ps.hasPermi('pm:workorder:list')")
    @GetMapping("/options")
    public R<List<PmmWorkOrder>> options() { return R.ok(service.options()); }
}
