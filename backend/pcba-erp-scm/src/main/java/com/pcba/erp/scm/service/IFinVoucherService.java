package com.pcba.erp.scm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.FinVoucherInterface;
import java.util.List;

/**
 * 财务凭证接口服务
 * pushPayableVoucher(payableId) : 对账完成后，生成应付凭证（借：原材料/进项税 贷：应付账款-供方）
 * pushPaymentVoucher(paymentId) : 付款成功后，生成付款凭证（借：应付账款-供方 贷：银行存款）
 * mockPush(voucherId)           : 模拟推送到财务系统 → push_status=2 success
 */
public interface IFinVoucherService {
    IPage<FinVoucherInterface> selectPage(int pageNum, int pageSize, String voucherNo, String voucherType, String bizSource, Long bizId, Integer pushStatus, String voucherDate);
    FinVoucherInterface getDetail(Long id);
    Long save(FinVoucherInterface entity);
    boolean update(FinVoucherInterface entity);
    boolean remove(Long id);
    List<FinVoucherInterface> options();

    /** 生成并推送应付凭证 */
    Long pushPayableVoucher(Long payableId);
    /** 生成并推送付款凭证 */
    Long pushPaymentVoucher(Long paymentId);
    /** 模拟推送成功 */
    boolean mockPush(Long voucherId);
    /** 作废凭证 */
    boolean cancelVoucher(Long voucherId, String reason);
}
