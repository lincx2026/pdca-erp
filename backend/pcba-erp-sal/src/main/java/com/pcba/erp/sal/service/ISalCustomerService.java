package com.pcba.erp.sal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalCustomer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ISalCustomerService {
    IPage<SalCustomer> selectPage(int pageNum, int pageSize, String customerCode, String customerName, String contact, String phone, String industry, String level, Integer status);
    SalCustomer getDetail(Long id);
    Long save(SalCustomer entity);
    boolean update(SalCustomer entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<SalCustomer> options();

    /** 生成下一个客户编码: CUS + yyyyMMdd + 4位序号 */
    String nextCustomerCode();

    /** 客户信用额度校验：应收未收+新单 > credit_limit(且credit_limit>0) 或存在逾期 → 抛异常 */
    void checkCreditLimit(Long customerId, BigDecimal newOrderCnyAmount);

    /** 客户信用占用查询：creditLimit/creditDays/unreceived/overdue/used/available/hasOverdue */
    Map<String, Object> getCredit(Long customerId);
}
