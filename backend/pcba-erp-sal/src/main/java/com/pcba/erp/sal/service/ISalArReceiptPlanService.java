package com.pcba.erp.sal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalArReceiptPlan;
import java.math.BigDecimal;
import java.util.List;

public interface ISalArReceiptPlanService {
    IPage<SalArReceiptPlan> selectPage(int pageNum, int pageSize, String planNo, Long receivableId, Long customerId, String customerName, Integer status, String planDate, Integer phaseNo);
    SalArReceiptPlan getDetail(Long id);
    Long save(SalArReceiptPlan entity);
    boolean update(SalArReceiptPlan entity);
    boolean remove(Long id);
    List<SalArReceiptPlan> listByReceivable(Long receivableId);

    /** 收款确认时回写 actualAmount / unpaid / status */
    boolean applyReceipt(Long planId, BigDecimal cnyReceivedAmount, Long receiptId);
    /** 取消收款回滚 */
    boolean rollbackReceipt(Long planId, BigDecimal cnyReceivedAmount, Long receiptId);
}
