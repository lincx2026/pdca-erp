package com.pcba.erp.sal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalSalesOrder;

import java.util.List;

public interface ISalSalesOrderService {
    IPage<SalSalesOrder> selectPage(int pageNum, int pageSize, String soNo, String soType, Long customerId, String customerName, Long salesRepId, Integer status, String deliveryDate);
    SalSalesOrder getDetail(Long id);
    Long save(SalSalesOrder entity);
    boolean update(SalSalesOrder entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<SalSalesOrder> options();

    /** 销售订单审核后生成WMS出库单(返回 stockOutId)，回填 stockOutId 并推进 status 2→3(已发货/在途) */
    Long generateStockOut(Long soId);
}
