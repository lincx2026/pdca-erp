package com.pcba.erp.scm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmSupplierPrice;

import java.util.List;

public interface IScmSupplierPriceService {
    IPage<ScmSupplierPrice> selectPage(int pageNum, int pageSize, Long supplierId, Long materialId,
                                       String materialCode, String materialName, String supplierName,
                                       String currency, Integer status);
    IPage<ScmSupplierPrice> selectGroupedPage(int pageNum, int pageSize, Long supplierId, Long materialId,
                                               String materialCode, String materialName, String supplierName,
                                               String currency, Integer status);
    ScmSupplierPrice getDetail(Long id);
    Long save(ScmSupplierPrice entity);
    boolean update(ScmSupplierPrice entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<ScmSupplierPrice> options();

    /** 提交: status 0 -> 1 */
    boolean submit(Long id);
    /** 审核通过: status 1 -> 2 */
    boolean audit(Long id);
    /** 停用: status -> 3 */
    boolean disable(Long id);

    /**
     * P0：批量把供应商报价（必须status=2生效）转换为采购订单
     * @param supplierId 供应商ID（必须所有报价都属于同一家供应商）
     * @param items 每个 priceId -> qty / deliveryDate
     * @param extra 采购订单头: poDate / deliveryDate / warehouseId / buyerId / paymentTerm / remark / reqSource
     * @return 新建的 po_id
     */
    Long convertToPurchaseOrder(Long supplierId,
                                List<java.util.Map<String, Object>> items,
                                java.util.Map<String, Object> extra);
}
