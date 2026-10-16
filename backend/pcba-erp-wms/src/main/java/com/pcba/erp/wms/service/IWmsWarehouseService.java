package com.pcba.erp.wms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.wms.entity.WmsWarehouse;

import java.util.List;

/**
 * 仓库主档 Service
 * - CRUD + 启用/停用 + 下拉列表
 */
public interface IWmsWarehouseService {

    /** 分页查询 */
    IPage<WmsWarehouse> selectWarehousePage(int pageNum, int pageSize,
                                             String warehouseName, String warehouseType, Integer status);

    /** 详情 */
    WmsWarehouse getDetail(Long warehouseId);

    /** 新增 (含仓库编码唯一性校验), 返回 warehouseId */
    Long saveWarehouse(WmsWarehouse warehouse);

    /** 修改 (含唯一性校验) */
    boolean updateWarehouse(WmsWarehouse warehouse);

    /** 删除 (启用状态禁止删除) */
    boolean removeWarehouse(Long warehouseId);

    /** 启用/停用切换 */
    boolean toggleStatus(Long warehouseId, Integer status);

    /** 下拉列表 (启用状态的仓库) */
    List<WmsWarehouse> warehouseOptions();
}