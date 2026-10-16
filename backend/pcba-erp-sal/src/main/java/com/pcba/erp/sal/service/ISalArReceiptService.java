package com.pcba.erp.sal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalArReceipt;
import java.util.List;

/**
 * 收款单服务
 * confirm:  status 1→2，回写 receivable.receivedAmount / plan.actualAmount
 * cancel :  status → 9 / 3(失败)，回滚 receivable + plan
 */
public interface ISalArReceiptService {
    IPage<SalArReceipt> selectPage(int pageNum, int pageSize, String receiptNo, Long customerId, String customerName, Integer status, String receiptDate, Long planId, Long receivableId);
    SalArReceipt getDetail(Long id);
    Long save(SalArReceipt entity);
    boolean update(SalArReceipt entity);
    boolean remove(Long id);
    List<SalArReceipt> options();

    /** 审批 (0→1) */
    boolean audit(Long id, String auditBy);
    /** 收款成功确认 (1→2)：回写应收+收款计划 */
    boolean confirm(Long id, String receiptBy);
    /** 标记收款失败 (1→3) */
    boolean fail(Long id, String failReason);
    /** 取消/作废收款单，回滚应收+收款计划(幂等) */
    boolean cancel(Long id, String reason);
}
