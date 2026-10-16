package com.pcba.erp.scm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmPurchaseReturn;
import com.pcba.erp.scm.entity.ScmPurchaseReturnLine;

import java.util.List;
import java.util.Map;

public interface IScmPurchaseReturnService {
    IPage<ScmPurchaseReturn> selectPage(int pageNum, int pageSize, String returnNo, Long poId, Long supplierId, String supplierName, Long warehouseId, Integer status, String returnDate);
    ScmPurchaseReturn getDetail(Long id);
    Long save(ScmPurchaseReturn entity);
    boolean update(ScmPurchaseReturn entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<ScmPurchaseReturn> options();

    /** 审核通过(≥2)的退货单生成 WMS 采购出库单；返回新的 WmsStockOut stockOutId；回填 return.stock_out_id 并推进 status=3 */
    Long generateStockOut(Long returnId);

    /** 保存退货单(含行), 校验行return_qty≤入库单行剩余可退, 返回returnId */
    Long saveReturn(ScmPurchaseReturn header, List<ScmPurchaseReturnLine> lines);

    /**
     * 退货单审核通过 status 0→2 (若1跳过到2);
     * 自动生成 WMS 出库 status3 确认并 publish StockOutConfirmedEvent (触发红字应付);
     * 回写PO行returnedQty;
     * 若has_replenishment=1自动生成请购行;
     * return auditPass 结果摘要。
     */
    Map<String, Object> auditPass(Long returnId);

    /**
     * 从入库单行选择生成草稿退货单
     *
     * @param body {warehouseId?, stockInLineIds:[Long], returnReason:String, hasReplenishment:0/1}
     * @return 新生成的退货单ID列表 (按 stockIn 分组，每组一张退货单)
     */
    List<Long> fromStockInLines(Map<String, Object> body);
}
