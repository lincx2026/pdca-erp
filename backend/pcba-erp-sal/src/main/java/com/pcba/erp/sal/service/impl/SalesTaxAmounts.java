package com.pcba.erp.sal.service.impl;

import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.sal.entity.SalSalesOrderLine;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** 订单行以含税价为输入，保留未税单价供现有应收流程使用。 */
final class SalesTaxAmounts {
    private SalesTaxAmounts() {}
    static void calculate(SalSalesOrderLine line) {
        BigDecimal price = line.getTaxInclusiveUnitPrice();
        BigDecimal qty = line.getOrderQty();
        BigDecimal rate = line.getTaxRate() == null ? BigDecimal.ZERO : line.getTaxRate();
        BigDecimal discount = line.getDiscountRate() == null ? BigDecimal.ZERO : line.getDiscountRate();
        if (price == null || price.signum() < 0 || qty == null || qty.signum() <= 0 || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0 || discount.signum() < 0 || discount.compareTo(BigDecimal.ONE) >= 0)
            throw new ServiceException("含税单价、数量、税率或折扣率无效");
        BigDecimal divisor = BigDecimal.ONE.add(rate);
        BigDecimal gross = price.multiply(qty).multiply(BigDecimal.ONE.subtract(discount)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.divide(divisor, 2, RoundingMode.HALF_UP);
        line.setUnitPrice(price.divide(divisor, 6, RoundingMode.HALF_UP));
        line.setLineAmount(gross);
        line.setTaxAmount(gross.subtract(net));
    }
}
