package com.pcba.erp.scm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmApPayment;
import java.util.List;

/**
 * 付款单服务
 * confirm:  status 1→2，回写 payable.paidAmount / plan.actualAmount，推送付款凭证
 * cancel :  status → 9 / 3(失败)，回滚 payable + plan，回滚凭证
 */
public interface IScmApPaymentService {
    IPage<ScmApPayment> selectPage(int pageNum, int pageSize, String paymentNo, Long supplierId, String supplierName, Integer status, String payDate, Long planId, Long payableId);
    ScmApPayment getDetail(Long id);
    Long save(ScmApPayment entity);
    boolean update(ScmApPayment entity);
    boolean remove(Long id);
    List<ScmApPayment> options();

    /** 审批 (0→1) */
    boolean audit(Long id, String auditBy);
    /** 付款成功确认 (1→2)：回写应付+付款计划+推送凭证 */
    boolean confirmPayment(Long id, String payBy);
    /** 标记付款失败 (1→3) */
    boolean markFailed(Long id, String failReason);
    /** 取消/作废付款单，回滚应付+付款计划(幂等) */
    boolean cancelPayment(Long id, String reason);
}
