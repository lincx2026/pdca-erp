package com.pcba.erp.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * WMS确认入库后发布的事件 → SCM监听并生成应付单（解耦 WMS / SCM 循环依赖）
 */
@Getter
public class StockInConfirmedEvent extends ApplicationEvent {
    private static final long serialVersionUID = 1L;
    private final Long stockInId;
    public StockInConfirmedEvent(Object source, Long stockInId) {
        super(source);
        this.stockInId = stockInId;
    }
}
