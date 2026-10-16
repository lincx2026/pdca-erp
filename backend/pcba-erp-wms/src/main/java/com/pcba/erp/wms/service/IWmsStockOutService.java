package com.pcba.erp.wms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pcba.erp.wms.entity.WmsStockOut;

/**
 * 出库单 Service
 * - 主表 + 明细 CRUD
 * - 状态机: 0草稿 → 1待审核 → (2已审核 → 3已出库) / 4已驳回
 * - 确认出库时扣减 wms_inventory 库存
 */
public interface IWmsStockOutService extends IService<WmsStockOut> {

    /** 分页查询（联表回填仓库名称） */
    IPage<WmsStockOut> selectStockOutPage(int pageNum, int pageSize,
                                          String orderNo, Long warehouseId,
                                          String outType, Integer status);

    /** 详情（含明细 lines） */
    WmsStockOut getDetail(Long stockOutId);

    /** 生成下一个出库单号: OUT + yyyyMMdd + 3位序号 */
    String nextOrderNo();

    /** 保存主表 + 明细，返回出库单ID */
    Long saveStockOut(WmsStockOut stockOut);

    /** 修改主表 + 明细（先删后插） */
    boolean updateStockOut(WmsStockOut stockOut);

    /** 删除（仅草稿/驳回可删，级联删除明细） */
    boolean removeStockOut(Long stockOutId);

    /* ============== 状态机 ============== */
    /** 提交审核: 0草稿 → 1待审核 */
    boolean submitAudit(Long stockOutId);

    /** 审核通过: 1待审核 → 2已审核 */
    boolean auditPass(Long stockOutId);

    /** 驳回: 1待审核 → 4已驳回（需 reason） */
    boolean reject(Long stockOutId, String reason);

    /** 确认出库: 2已审核 → 3已出库，扣减库存；采购退货类同步反写 PO 行 returnedQty */
    boolean confirmStockOut(Long stockOutId);

    /** 取消出库: 3已出库 → 2已审核，回滚库存台账 + PO 行 returnedQty 回滚 */
    boolean cancelStockOut(Long stockOutId);
}