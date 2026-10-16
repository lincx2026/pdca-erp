package com.pcba.erp.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * WMS确认出库(采购退货)后发布的事件 → SCM监听并生成红字应付单
 */
@Getter
public class StockOutConfirmedEvent extends ApplicationEvent {
    private static final long serialVersionUID = 1L;
    private final Long stockOutId;
    public StockOutConfirmedEvent(Object source, Long stockOutId) {
        super(source);
        this.stockOutId = stockOutId;
    }
}
