package com.pcba.erp.sal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalArReceivable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * SAL应收单服务
 * 核心：① 手工生成应收单(保持模块独立，不使用事件驱动)
 *      ② 对账 reconcile / 作废 cancel
 *      ③ 收款确认/取消(回写 received_amount + 状态)
 *      ④ 信用额度校验(供SO下单拦截)
 *      ⑤ 看板统计
 */
public interface ISalArReceivableService {
    IPage<SalArReceivable> selectPage(int pageNum, int pageSize, String receivableNo, Long customerId, String customerName, Integer status, String bizDate, String dueDate, Integer isOverdue);
    SalArReceivable getDetail(Long id);
    Long save(SalArReceivable entity);
    boolean update(SalArReceivable entity);
    boolean remove(Long id);
    List<SalArReceivable> options();

    // --- 核心业务 ---
    /**
     * WMS出库确认事件驱动：按 stockOutId 自动生成销售应收单（outType=SALES 才处理）
     * 幂等：已存在同 stockOutId 的应收行则直接返回既有 receivableId，不重复生成。
     * @return 生成的 receivableId；非销售类出库返回 null
     */
    Long generateFromSalesOut(Long stockOutId);

    /**
     * WMS入库确认事件驱动：按 stockInId 自动生成销售退货红字应收单（inType=RETURN 且明细含 returnId 才处理）
     * 红字（冲销）：金额取负，bizType=SALES_RETURN，明细 lineSrc=SALES_RETURN_IN，关联 returnId/returnLineId/soId/soLineId
     * 幂等：已存在同 returnId 的红字应收行则直接返回既有 receivableId，不重复生成。
     * @return 生成的 receivableId；非销售退货入库返回 null
     */
    Long generateFromSalesReturnStockIn(Long stockInId);

    // --- 对账流程(两级) ---
    /** 提交对账：0待对账 → 1对账中(等待客户签字回签)；录入 attachment/remark */
    boolean submitReconcile(Long receivableId, String signBy, String attachment, String remark, String operator);
    /** 确认对账：1对账中 → 2对账完成(客户已签字)；必填 signBy(客户签字人)/signTime/voucherId */
    boolean confirmReconcile(Long receivableId, String signBy, java.time.LocalDateTime signTime, Long voucherId, String operator);
    /** 撤销对账(反审)：2对账完成 → 0待对账；约束 receivedAmount=0 */
    boolean undoReconcile(Long receivableId);
    /** 批量对账：按 customer + bizDate 范围，把 status=0 的应收单 0→2(批量录入签字细节后续补) */
    int batchReconcile(Long customerId, String dateFrom, String dateTo, String operator);

    /** 作废应收单 */
    boolean cancel(Long receivableId);

    /** 生成收款计划(分期)：ratio数组比例之和=100，默认 [100] 一次性 */
    boolean generatePlans(Long receivableId, List<Map<String,Object>> phases);

    /** 收款确认时回写 received_amount / unreceived_amount / status，供 ISalArReceiptService 内部调用 */
    boolean applyReceipt(Long receivableId, BigDecimal cnyReceivedAmount, Long receiptId);
    /** 取消收款时回滚 received_amount / unreceived_amount / status */
    boolean rollbackReceipt(Long receivableId, BigDecimal cnyReceivedAmount, Long receiptId);

    // --- 信用管控(SO下单调用) ---
    /** 客户未收总额(不含作废) = Σ(cny_total_amount - received_amount) */
    BigDecimal sumUnreceived(Long customerId);
    /** 客户逾期未收 */
    BigDecimal sumOverdue(Long customerId);
    /** SO下单拦截：如有逾期 → 抛异常；未收总额+本次新单金额 > credit_limit(且credit_limit>0) → 抛异常 */
    void checkCreditLimit(Long customerId, BigDecimal newOrderCnyAmount);

    // --- 看板 ---
    Map<String,Object> kanbanSummary(Long customerId);
    List<Map<String,Object>> topCustomers(Long customerId, int limit);
    List<Map<String,Object>> aging(Long customerId);
}
