package com.pcba.erp.scm.event;

import com.pcba.erp.common.event.StockInConfirmedEvent;
import com.pcba.erp.common.event.StockOutConfirmedEvent;
import com.pcba.erp.scm.service.IScmApPayableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 监听 WMS 出入库确认事件 → 自动生成应付单（入库蓝字/退货红字）
 * 与 WMS 处于同一事务中：若 WMS 主事务回滚，则应付生成也会一同回滚。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScmApPayableEventListener {

    private final IScmApPayableService payableService;

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onStockInConfirmed(StockInConfirmedEvent event) {
        Long id = event.getStockInId();
        log.info("[ScmAp] 收到入库确认事件 stockInId={}", id);
        try {
            payableService.generateFromStockIn(id);
        } catch (Exception ex) {
            log.error("[ScmAp] 入库自动生成应付单失败 stockInId={}", id, ex);
            throw ex;
        }
    }

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onStockOutConfirmed(StockOutConfirmedEvent event) {
        Long id = event.getStockOutId();
        log.info("[ScmAp] 收到出库确认(退货)事件 stockOutId={}", id);
        try {
            payableService.generateFromReturnOut(id);
        } catch (Exception ex) {
            log.error("[ScmAp] 退货自动生成红字应付失败 stockOutId={}", id, ex);
            throw ex;
        }
    }
}
