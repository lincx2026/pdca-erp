package com.pcba.erp.sal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalSalesReturn;
import com.pcba.erp.sal.entity.SalSalesReturnLine;

import java.util.List;

public interface ISalSalesReturnService {
    IPage<SalSalesReturn> selectPage(int pageNum, int pageSize, String returnNo, Long customerId, String customerName, Long soId, Integer status, String returnDate);
    SalSalesReturn getDetail(Long id);
    Long save(SalSalesReturn entity);
    boolean update(SalSalesReturn entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<SalSalesReturn> options();

    /**
     * 销售退货 → WMS 退货入库单生成（对称采购退货 generateStockOut）
     * - 仅状态=2(已审核)且 stockInId 为空可生成
     * - 构造入库单(inType=RETURN) + 明细，回填 returnId/returnLineId/soId/soLineId 链路
     * - 回填 stockInId 并推进退货单 status 2→3(入库中)
     * @return 生成的 WMS 入库单 ID
     */
    Long generateStockIn(Long returnId);

    /**
     * 退货质检录入（WMS入库确认后 qc_status=0待检 时调用）
     * - 录入各行 qcQty(合格数)/qcDisposition/qcRemark + 主单 disposition/qcRemark
     * - 汇总主单 qc_status: 1合格(全行合格)/2部分不良/3全不良
     * - 按合格数重算 refundAmount = sum(qcQty * unitPrice)
     * - 推进 status 3→4(已完成)
     * @param returnId 退货单ID
     * @param disposition 整体处置 ACCEPT/RETURN_SUPPLIER/CONCESSION/SCRAP
     * @param qcRemark 质检备注
     * @param lines 明细质检录入(需带lineId+qcQty+qcDisposition+qcRemark)
     * @param operator 操作人
     */
    boolean submitQc(Long returnId, String disposition, String qcRemark, List<SalSalesReturnLine> lines, String operator);
}
