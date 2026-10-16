package com.pcba.erp.scm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmApPaymentPlan;
import java.util.List;

public interface IScmApPaymentPlanService {
    IPage<ScmApPaymentPlan> selectPage(int pageNum, int pageSize, String planNo, Long payableId, Long supplierId, String supplierName, Integer status, String planDate, Integer phaseNo);
    ScmApPaymentPlan getDetail(Long id);
    Long save(ScmApPaymentPlan entity);
    boolean update(ScmApPaymentPlan entity);
    boolean remove(Long id);
    List<ScmApPaymentPlan> listByPayable(Long payableId);

    /** 付款确认时回写 actualAmount / unpaid / status */
    boolean applyPayment(Long planId, java.math.BigDecimal cnyPaidAmount, Long paymentId);
    /** 取消付款回滚 */
    boolean rollbackPayment(Long planId, java.math.BigDecimal cnyPaidAmount, Long paymentId);
}
