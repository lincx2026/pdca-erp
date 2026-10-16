package com.pcba.erp.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.wms.entity.WmsWarehouse;
import com.pcba.erp.wms.mapper.WmsWarehouseMapper;
import com.pcba.erp.wms.service.IWmsWarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 仓库主档 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsWarehouseServiceImpl implements IWmsWarehouseService {

    private final WmsWarehouseMapper warehouseMapper;

    @Override
    public IPage<WmsWarehouse> selectWarehousePage(int pageNum, int pageSize,
                                                    String warehouseName, String warehouseType, Integer status) {
        Page<WmsWarehouse> page = new Page<>(pageNum, pageSize);
        return warehouseMapper.selectWarehousePage(page, warehouseName, warehouseType, status);
    }

    @Override
    public WmsWarehouse getDetail(Long warehouseId) {
        if (warehouseId == null) throw new ServiceException("仓库ID不能为空");
        WmsWarehouse w = warehouseMapper.selectById(warehouseId);
        if (w == null) throw new ServiceException("仓库不存在");
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveWarehouse(WmsWarehouse warehouse) {
        if (warehouse.getWarehouseCode() != null) {
            int dup = warehouseMapper.countDuplicateCode(warehouse.getWarehouseCode(), null);
            if (dup > 0) throw new ServiceException("仓库编码【" + warehouse.getWarehouseCode() + "】已存在");
        }
        if (warehouse.getStatus() == null) warehouse.setStatus(1); // 默认启用
        warehouseMapper.insert(warehouse);
        return warehouse.getWarehouseId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateWarehouse(WmsWarehouse warehouse) {
        if (warehouse.getWarehouseId() == null) throw new ServiceException("仓库ID不能为空");
        WmsWarehouse old = warehouseMapper.selectById(warehouse.getWarehouseId());
        if (old == null) throw new ServiceException("仓库不存在");
        if (warehouse.getWarehouseCode() != null) {
            int dup = warehouseMapper.countDuplicateCode(warehouse.getWarehouseCode(), warehouse.getWarehouseId());
            if (dup > 0) throw new ServiceException("仓库编码已存在");
        }
        return warehouseMapper.updateById(warehouse) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeWarehouse(Long warehouseId) {
        WmsWarehouse w = warehouseMapper.selectById(warehouseId);
        if (w == null) throw new ServiceException("仓库不存在");
        if (Integer.valueOf(1).equals(w.getStatus())) {
            throw new ServiceException("启用状态的仓库不能删除, 请先停用");
        }
        // TODO: 校验是否被库存台账/入出库单引用, 引用则禁止删除
        return warehouseMapper.deleteById(warehouseId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleStatus(Long warehouseId, Integer status) {
        if (warehouseId == null) throw new ServiceException("仓库ID不能为空");
        if (status == null || (status != 0 && status != 1)) throw new ServiceException("状态值非法");
        WmsWarehouse w = warehouseMapper.selectById(warehouseId);
        if (w == null) throw new ServiceException("仓库不存在");
        w.setStatus(status);
        return warehouseMapper.updateById(w) > 0;
    }

    @Override
    public List<WmsWarehouse> warehouseOptions() {
        return warehouseMapper.selectList(new LambdaQueryWrapper<WmsWarehouse>()
                .eq(WmsWarehouse::getStatus, 1)
                .orderByAsc(WmsWarehouse::getWarehouseId));
    }
}