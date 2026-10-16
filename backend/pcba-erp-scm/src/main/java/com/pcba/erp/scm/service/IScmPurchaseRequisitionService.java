package com.pcba.erp.scm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmPurchaseRequisition;

import java.util.List;
import java.util.Map;

public interface IScmPurchaseRequisitionService {
    IPage<ScmPurchaseRequisition> selectPage(int pageNum, int pageSize, String requisitionNo, String title, String applicantName, Long deptId, Integer status, String requiredDate);
    ScmPurchaseRequisition getDetail(Long id);
    Long save(ScmPurchaseRequisition entity);
    boolean update(ScmPurchaseRequisition entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<ScmPurchaseRequisition> options();

    /**
     * T5-2: 扫描 pdm_material 安全库存下限，比较 wms_inventory 汇总可用量，低于 (reorder_point+safety_stock)
     * 则生成/追加到当日草稿请购行。返回 requisitionId。
     *
     * @param options { warehouseId?, applicantId?, requiredDateOffsetDays(default 14) }
     * @return requisitionId
     */
    Long generateFromSafetyStock(Map<String, Object> options);

    /**
     * T5-3: 请购单行（已审核 requisition.status>=2 & line.status=2）批量转PO，
     * 按默认供方 + 其 ScmSupplierPrice 阶梯价匹配，并按 (supplierId,currency,deliveryDate) 合并PO。
     *
     * @return { poIds:[], missingLineIds:[], warnings:[], mergedCount }
     */
    Map<String, Object> convertToPurchaseOrder(List<Long> lineIds, Map<String, Object> commonHeader);
}
