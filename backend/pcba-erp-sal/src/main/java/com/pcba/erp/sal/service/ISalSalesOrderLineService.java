package com.pcba.erp.sal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalSalesOrderLine;

import java.util.List;

public interface ISalSalesOrderLineService {
    IPage<SalSalesOrderLine> selectPage(int pageNum, int pageSize, Long soId, String materialCode, String materialName);
    SalSalesOrderLine getDetail(Long id);
    Long save(SalSalesOrderLine entity);
    boolean update(SalSalesOrderLine entity);
    boolean remove(Long id);

    /** 物料回填：按 materialId 从PDM物料表带出编码/名称/规格/单位 */
    void fillMaterial(SalSalesOrderLine entity);
    /** 重算销售订单主单合计(undiscAmount/totalAmount/taxAmount) */
    void recomputeHeader(Long soId);
}
