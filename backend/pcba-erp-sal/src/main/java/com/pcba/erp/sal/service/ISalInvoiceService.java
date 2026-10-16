package com.pcba.erp.sal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalArReceivable;
import com.pcba.erp.sal.entity.SalInvoice;
import com.pcba.erp.sal.entity.SalInvoiceLine;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ISalInvoiceService {
    /** 分页查询 */
    IPage<SalInvoice> selectPage(int pageNum, int pageSize, String invoiceNo, Long customerId, String invoiceType, Integer status, LocalDate dateFrom, LocalDate dateTo);
    /** 详情(含明细) */
    Map<String, Object> getDetail(Long id);
    /** 新建草稿开票单(含明细) */
    Long save(SalInvoice entity, List<SalInvoiceLine> lines);
    /** 编辑(仅草稿) */
    boolean update(SalInvoice entity, List<SalInvoiceLine> lines);
    /** 删除(仅草稿) */
    boolean remove(Long id);
    /** 确认开票 0→1(回写应收invoiced_amount,校验部分金额≤未开票余额) */
    boolean issue(Long id, String operator);
    /** 作废 1→2(回退invoiced_amount) */
    boolean cancel(Long id);
    /** 查询客户可开票应收单 */
    List<SalArReceivable> invoiceableReceivables(Long customerId);
    /** 生成发票号 INV+yyyyMMdd+3位 */
    String generateInvoiceNo();
}
