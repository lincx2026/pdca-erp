package com.pcba.erp.sal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalSalesReturnLine;

public interface ISalSalesReturnLineService {
    IPage<SalSalesReturnLine> selectPage(int pageNum, int pageSize, Long returnId, String materialCode, String materialName);
    SalSalesReturnLine getDetail(Long id);
    Long save(SalSalesReturnLine entity);
    boolean update(SalSalesReturnLine entity);
    boolean remove(Long id);

    /** 物料回填：按 materialId 从PDM物料表带出编码/名称/规格/单位 */
    void fillMaterial(SalSalesReturnLine entity);
    /** 重算销售退货主单合计(totalAmount) */
    void recomputeHeader(Long returnId);
}
