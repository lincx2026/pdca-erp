package com.pcba.erp.scm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmDeliveryNote;

import java.util.List;
import java.util.Map;

public interface IScmDeliveryNoteService {
    IPage<ScmDeliveryNote> selectPage(int pageNum, int pageSize, String deliveryNo, String poNo, Long supplierId, String supplierName, Long warehouseId, Integer status, String deliveryDate);
    ScmDeliveryNote getDetail(Long id);
    Long save(ScmDeliveryNote entity);
    boolean update(ScmDeliveryNote entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<ScmDeliveryNote> options();
    /**
     * 根据到货单生成 WMS 采购入库单：
     * - 将到货明细迁移为 wms_stock_in + wms_stock_in_line（数量用 acceptedQty，缺省按 deliveryQty）
     * - 回填 delivery.stock_in_id 并推进状态到 4 入库完成
     * - 返回新入库单 stockInId
     */
    Long generateStockIn(Long deliveryId);

    /**
     * IQC QC 分流判定（事务）：
     *   - 校验 delivery/line 归属 + 数量守恒 + 拒收原因码
     *   - 更新 delivery_line.qc* 字段与行 qcStatus
     *   - 汇总更新 delivery 头 qc_status
     *   - accepted/concession: WH-IQC → 实仓调拨（库存+WmsStockIn确认入库）
     *   - rejected: 生成 scm_purchase_return 草稿(status=0, qc_flag=1) + 明细行
     *   - scrapped: 扣减 WH-IQC 库存
     * @return {deliveryId, qcStatus, linesUpdated, stockedQty, returnsGenerated:[{returnId,totalQty}], scrappedQty}
     */
    Map<String,Object> qcJudge(Long deliveryId, List<Map<String,Object>> judgements);

    /**
     * 从采购订单(PO)快速生成到货通知单草稿：
     *   - 头: 从 ScmPurchaseOrder 复制 supplierId/Name/warehouseId 等，status=0/qc_status=0
     *   - 行: 遍历 ScmPurchaseOrderLine，lineQty = orderQty - ΣreceivedQty - ΣreturnedQty（≥0才产生行）
     *   - 单号: DN-{yyyyMMdd}-{seq6位}
     * @param poId 采购订单ID
     * @param headerExtra 头表扩展字段（可选覆盖：deliveryDate/warehouseId/remark 等）
     * @return 新生成的 deliveryId
     */
    Long createFromPurchaseOrder(Long poId, Map<String,Object> headerExtra);
}
