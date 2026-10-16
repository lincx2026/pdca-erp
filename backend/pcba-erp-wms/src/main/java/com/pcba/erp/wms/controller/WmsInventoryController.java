package com.pcba.erp.wms.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.wms.entity.WmsInventory;
import com.pcba.erp.wms.service.IWmsInventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 库存台账 Controller
 *   - 分页查询 + 详情 (入出库更新库存由 stock-in/stock-out 服务调用 IWmsInventoryService)
 */
@Tag(name = "库存台账管理")
@RestController
@RequestMapping("/wms/inventory")
@RequiredArgsConstructor
public class WmsInventoryController {

    private final IWmsInventoryService inventoryService;

    @Operation(summary = "库存分页查询 (联表仓库+物料)")
    @PreAuthorize("@ps.hasPermi('wms:inventory:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) Long warehouseId,
                                 @RequestParam(required = false) String materialCode,
                                 @RequestParam(required = false) String materialName,
                                 @RequestParam(required = false) Integer status) {
        IPage<WmsInventory> page = inventoryService.selectInventoryPage(pageNum, pageSize, warehouseId, materialCode, materialName, status);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "库存详情")
    @PreAuthorize("@ps.hasPermi('wms:inventory:list')")
    @GetMapping("/{id}")
    public R<WmsInventory> get(@PathVariable Long id) {
        return R.ok(inventoryService.getDetail(id));
    }
}