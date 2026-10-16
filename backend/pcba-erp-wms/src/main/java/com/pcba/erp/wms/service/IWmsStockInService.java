package com.pcba.erp.wms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.wms.entity.WmsStockIn;

/**
 * 入库单 Service
 * - 主档 CRUD + 状态机 (0草稿 → 1待审核 → 2已审核 → 3已入库 / 4已驳回)
 * - 确认入库时同步更新库存台账
 */
public interface IWmsStockInService {

    /** 分页查询（含数据权限，回填 warehouseName） */
    IPage<WmsStockIn> selectStockInPage(int pageNum, int pageSize,
                                        String orderNo, Long warehouseId,
                                        String inType, Integer status);

    /** 详情（含明细 lines） */
    WmsStockIn getDetail(Long stockInId);

    /** 生成入库单号: IN + yyyyMMdd + 3位序号 */
    String nextOrderNo();

    /** 保存主表+明细，自动计算 totalQty/totalAmount，返回 stockInId */
    Long saveStockIn(WmsStockIn stockIn);

    /** 修改主表+明细（仅草稿/驳回可改） */
    boolean updateStockIn(WmsStockIn stockIn);

    /** 删除（仅草稿/驳回可删，级联删除明细） */
    boolean removeStockIn(Long stockInId);

    /* ============== 状态机 ============== */
    /** 草稿(0) → 待审核(1) */
    boolean submitAudit(Long stockInId);

    /** 待审核(1) → 已审核(2)，审核通过 */
    boolean auditPass(Long stockInId, String auditBy);

    /** 待审核(1) → 已驳回(4)，驳回 */
    boolean reject(Long stockInId, String auditBy, String rejectReason);

    /** 已审核(2) → 已入库(3)，确认入库，同时增加库存；采购入库同步反写 PO 行 receivedQty 与 PO 主单 status */
    boolean confirmStockIn(Long stockInId);

    /** 已入库(3) → 已审核(2)，取消入库：扣减库存台账 + 回滚采购订单行 receivedQty / PO 主单状态 */
    boolean cancelStockIn(Long stockInId);
}