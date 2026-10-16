package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.PmmWorkOrder;

import java.util.List;

public interface IPmmWorkOrderService {
    IPage<PmmWorkOrder> selectPage(int pageNum, int pageSize, String orderNo, String materialCode, String materialName,
                                    String sourceType, Integer status, String workCenter, String saleOrderNo);
    PmmWorkOrder getDetail(Long id);
    Long save(PmmWorkOrder entity);
    boolean update(PmmWorkOrder entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<PmmWorkOrder> options();
}
