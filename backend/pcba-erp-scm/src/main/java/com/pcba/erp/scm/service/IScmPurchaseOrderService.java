package com.pcba.erp.scm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmPurchaseOrder;

import java.util.List;
import java.util.Map;

public interface IScmPurchaseOrderService {
    IPage<ScmPurchaseOrder> selectPage(int pageNum, int pageSize, String poNo, String poType, String bizCategory, Long supplierId, String supplierName, Long buyerId, Integer status, String deliveryDate);
    ScmPurchaseOrder getDetail(Long id);
    Long save(ScmPurchaseOrder entity);
    boolean update(ScmPurchaseOrder entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<ScmPurchaseOrder> options();

    /**
     * P1: 从采购订单生成一张入库单（可重复调用多次，直到PO.status=5全部到货）
     * 安全策略: 实际入库量 = min(lineQtyParam, orderQty - receivedQty - returnedQty)
     *          即 订单剩余可入库量 = max(orderQty - received - returned, 0)
     * @param poId 采购订单ID（要求 status 已下达=3 或 已审核=2 或 部分到货=4）
     * @param body 请求体 { warehouseId, stockInNo?, stockInDate?, remark?,
     *                   lines: [ {lineId, qty, batchNo?, unitPrice?} ] }
     *              lineId=poLineId  仓库必填（原材料仓默认 WH-01 id=9/1/...）
     * @return 新生成的 stock_in_id（经 confirmStockIn 已加库存 + 反写 receivedQty）
     */
    Long generateStockIn(Long poId, Map<String, Object> body);
}
