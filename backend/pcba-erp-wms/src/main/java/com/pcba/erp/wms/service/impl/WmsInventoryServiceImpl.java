package com.pcba.erp.wms.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.wms.entity.WmsInventory;
import com.pcba.erp.wms.mapper.WmsInventoryMapper;
import com.pcba.erp.wms.service.IWmsInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 库存台账 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsInventoryServiceImpl implements IWmsInventoryService {

    private final WmsInventoryMapper inventoryMapper;

    @Override
    public IPage<WmsInventory> selectInventoryPage(int pageNum, int pageSize,
                                                    Long warehouseId, String materialCode,
                                                    String materialName, Integer status) {
        Page<WmsInventory> page = new Page<>(pageNum, pageSize);
        return inventoryMapper.selectInventoryPage(page, warehouseId, materialCode, materialName, status);
    }

    @Override
    public WmsInventory getDetail(Long inventoryId) {
        if (inventoryId == null) throw new ServiceException("库存ID不能为空");
        WmsInventory inv = inventoryMapper.selectById(inventoryId);
        if (inv == null) throw new ServiceException("库存记录不存在");
        return inv;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int increaseInventory(Long warehouseId, Long materialId, String batchNo,
                                 BigDecimal quantity, String unit) {
        validateChange(warehouseId, materialId, quantity);
        return inventoryMapper.increase(warehouseId, materialId, batchNo, quantity, unit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int decreaseInventory(Long warehouseId, Long materialId, String batchNo, BigDecimal quantity) {
        validateChange(warehouseId, materialId, quantity);
        if (inventoryMapper.decrease(warehouseId, materialId, batchNo, quantity) != 1) {
            throw new ServiceException("可用库存不足、库存不存在或已冻结");
        }
        return 1;
    }

    private void validateChange(Long warehouseId, Long materialId, BigDecimal quantity) {
        if (warehouseId == null || materialId == null) throw new ServiceException("仓库和物料不能为空");
        if (quantity == null || quantity.signum() <= 0) throw new ServiceException("库存变动数量必须大于0");
    }
}