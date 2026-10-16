package com.pcba.erp.wms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.wms.entity.WmsInventory;

import java.math.BigDecimal;

/**
 * 库存台账 Service
 * - 分页查询 + 详情
 * - 入出库时调用 increase/decrease 更新库存
 */
public interface IWmsInventoryService {

    /** 分页查询 (联表仓库+物料) */
    IPage<WmsInventory> selectInventoryPage(int pageNum, int pageSize,
                                           Long warehouseId, String materialCode,
                                           String materialName, Integer status);

    /** 详情 */
    WmsInventory getDetail(Long inventoryId);

    /**
     * 库存增加 (入库时调用): 存在则累加, 不存在则新增
     */
    int increaseInventory(Long warehouseId, Long materialId, String batchNo,
                          BigDecimal quantity, String unit);

    /**
     * 库存减少 (出库时调用): 校验可用库存, 扣减 quantity/availableQty
     */
    int decreaseInventory(Long warehouseId, Long materialId, String batchNo,
                          BigDecimal quantity);
}