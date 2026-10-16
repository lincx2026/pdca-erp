package com.pcba.erp.scm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmApPayable;

import java.util.List;
import java.util.Map;

/**
 * SCM应付单服务
 * 核心：① 从WMS入库/退货自动生成应付单(按供应商+到期日合并)
 *      ② 手工对账 reconcileManual / 作废
 *      ③ 付款确认/取消(回写 paid_amount + 状态)
 *      ④ 信用额度校验(供PO下单拦截)
 */
public interface IScmApPayableService {
    IPage<ScmApPayable> selectPage(int pageNum, int pageSize, String payableNo, Long supplierId, String supplierName, Integer status, String bizDate, String dueDate, Integer isOverdue);
    ScmApPayable getDetail(Long id);
    Long save(ScmApPayable entity);
    boolean update(ScmApPayable entity);
    boolean remove(Long id);
    List<ScmApPayable> options();

    // --- 核心业务 ---
    /** 根据WMS入库单自动生成应付单，按供应商+due_date合并；返回本次生成的应付单ID列表 */
    List<Long> generateFromStockIn(Long stockInId);
    /** 根据WMS出库单(采购退货)生成红字应付，金额为负 */
    List<Long> generateFromReturnOut(Long stockOutId);

    /** 手工对账：状态 0/1 → 2，同时推送应付凭证 */
    boolean reconcileManual(Long payableId, String reconcileBy);
    /** 作废 */
    boolean cancelPayable(Long payableId);

    /** 付款确认时回写 paid_amount / unpaid_amount / status，供 IScmApPaymentService 内部调用 */
    boolean applyPayment(Long payableId, java.math.BigDecimal cnyPaidAmount, Long paymentId);
    /** 取消付款时回滚 paid_amount / unpaid_amount / status */
    boolean rollbackPayment(Long payableId, java.math.BigDecimal cnyPaidAmount, Long paymentId);

    /** 生成付款计划（分期）：ratio数组比例之和=100，默认 [100] 一次性；planDate从due_date按比例拆分 */
    boolean generatePlans(Long payableId, List<Map<String,Object>> phases);

    // --- 信用管控(PO下单调用) ---
    /** 供方未付总额(不含作废) = Σ(cny_total_amount - paid_amount) */
    java.math.BigDecimal sumUnpaid(Long supplierId);
    /** 供方逾期未付 */
    java.math.BigDecimal sumOverdue(Long supplierId);
    /** PO下单拦截：如有逾期 → 抛异常；未付总额+本次新单金额 > credit_limit(且credit_limit>0) → 抛异常 */
    void checkCreditLimit(Long supplierId, java.math.BigDecimal newOrderCnyAmount);

    // --- 看板 ---
    Map<String,Object> getKanbanSummary(Long supplierId);
    List<Map<String,Object>> getTopSuppliers(Long supplierId, int limit);
    List<Map<String,Object>> getAgingBuckets(Long supplierId);
}
