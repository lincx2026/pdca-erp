package com.pcba.erp.sal.event;

import com.pcba.erp.common.event.StockInConfirmedEvent;
import com.pcba.erp.common.event.StockOutConfirmedEvent;
import com.pcba.erp.sal.service.ISalArReceivableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 监听 WMS 出入库确认事件 → 自动生成销售应收单：
 *  · StockOutConfirmedEvent（outType=SALES）→ 蓝字应收（正向）
 *  · StockInConfirmedEvent（inType=RETURN 且明细含 returnId）→ 红字应收（冲销）
 * 与 WMS 处于同一事务：若 WMS 主事务回滚，应收生成也一同回滚。
 * 幂等由 generateFromSalesOut / generateFromSalesReturnStockIn 内部保证（分别按 stock_out_id / return_id 查重）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalArReceivableEventListener {

    private final ISalArReceivableService receivableService;

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onStockOutConfirmed(StockOutConfirmedEvent event) {
        Long id = event.getStockOutId();
        log.info("[SalAr] 收到出库确认事件 stockOutId={}", id);
        try {
            Long receivableId = receivableService.generateFromSalesOut(id);
            if (receivableId != null) {
                log.info("[SalAr] 销售出库自动生成应收单 stockOutId={} → receivableId={}", id, receivableId);
            }
        } catch (Exception ex) {
            log.error("[SalAr] 销售出库自动生成应收单失败 stockOutId={}", id, ex);
            throw ex;
        }
    }

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onStockInConfirmed(StockInConfirmedEvent event) {
        Long id = event.getStockInId();
        log.info("[SalAr] 收到入库确认事件 stockInId={}", id);
        try {
            Long receivableId = receivableService.generateFromSalesReturnStockIn(id);
            if (receivableId != null) {
                log.info("[SalAr] 销售退货入库自动生成红字应收 stockInId={} → receivableId={}", id, receivableId);
            }
        } catch (Exception ex) {
            log.error("[SalAr] 销售退货入库自动生成红字应收失败 stockInId={}", id, ex);
            throw ex;
        }
    }
}
