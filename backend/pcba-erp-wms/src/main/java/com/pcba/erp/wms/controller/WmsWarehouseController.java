package com.pcba.erp.wms.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.wms.entity.WmsWarehouse;
import com.pcba.erp.wms.service.IWmsWarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 仓库主档 Controller
 *   - 基础 CRUD + 启用/停用 + 下拉列表
 */
@Tag(name = "仓库主档管理")
@RestController
@RequestMapping("/wms/warehouse")
@RequiredArgsConstructor
public class WmsWarehouseController {

    private final IWmsWarehouseService warehouseService;

    @Operation(summary = "仓库分页查询")
    @PreAuthorize("@ps.hasPermi('wms:warehouse:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String warehouseName,
                                 @RequestParam(required = false) String warehouseType,
                                 @RequestParam(required = false) Integer status) {
        IPage<WmsWarehouse> page = warehouseService.selectWarehousePage(pageNum, pageSize, warehouseName, warehouseType, status);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "仓库详情")
    @PreAuthorize("@ps.hasPermi('wms:warehouse:list')")
    @GetMapping("/{id}")
    public R<WmsWarehouse> get(@PathVariable Long id) {
        return R.ok(warehouseService.getDetail(id));
    }

    @Operation(summary = "新增仓库 (仓库编码唯一性校验)")
    @PreAuthorize("@ps.hasPermi('wms:warehouse:add')")
    @OperLog(title = "仓库主档", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody WmsWarehouse warehouse) {
        Long id = warehouseService.saveWarehouse(warehouse);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改仓库")
    @PreAuthorize("@ps.hasPermi('wms:warehouse:edit')")
    @OperLog(title = "仓库主档", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody WmsWarehouse warehouse) {
        return warehouseService.updateWarehouse(warehouse) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除仓库 (仅停用状态可删)")
    @PreAuthorize("@ps.hasPermi('wms:warehouse:remove')")
    @OperLog(title = "仓库主档", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return warehouseService.removeWarehouse(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "启用仓库")
    @PreAuthorize("@ps.hasPermi('wms:warehouse:edit')")
    @OperLog(title = "仓库主档", businessType = 2)
    @PutMapping("/enable/{id}")
    public R<Integer> enable(@PathVariable Long id) {
        return warehouseService.toggleStatus(id, 1) ? R.ok(1) : R.fail("启用失败");
    }

    @Operation(summary = "停用仓库")
    @PreAuthorize("@ps.hasPermi('wms:warehouse:edit')")
    @OperLog(title = "仓库主档", businessType = 2)
    @PutMapping("/disable/{id}")
    public R<Integer> disable(@PathVariable Long id) {
        return warehouseService.toggleStatus(id, 0) ? R.ok(1) : R.fail("停用失败");
    }

    @Operation(summary = "仓库下拉列表 (启用状态)")
    @PreAuthorize("@ps.hasPermi('wms:warehouse:list')")
    @GetMapping("/options")
    public R<List<WmsWarehouse>> options() {
        return R.ok(warehouseService.warehouseOptions());
    }
}