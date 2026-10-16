package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.FasProductStock;

public interface IPmmFasStockService {

    /* ======================= 台账 (原逻辑保留, 新增 stockType 维度实现 FG/ACC 软隔离) ======================= */
    IPage<FasProductStock> selectPage(int pageNum, int pageSize, String productName, String batchNo, String warehouse);
    /** 按业务域(FG 成品 / ACC 配件)过滤台账, 用于成品仓 vs 配件仓数据隔离 */
    IPage<FasProductStock> selectPageByScope(int pageNum, int pageSize, String stockType,
                                             String productName, String batchNo, String warehouse);
    FasProductStock getById(Long id);
    void stockOut(Long stockId, Integer qty);
    void remove(Long id);

    /* ======================= 完工入库单 ======================= */
    /** 完工入库单分页查询 (docType=INBOUND) */
    IPage<FasProductStock> selectInboundPage(int pageNum, int pageSize, Integer docStatus, String docNo,
                                              String productName, String batchNo, String warehouse);

    /** 生成完工入库单草稿 (后道组装工单入库入口) */
    Long createInboundDraftFromOrder(Long orderId, String warehouse, String batchNo, Integer qty, String remark);

    /** 提交审核 (草稿→待审核 0→1) */
    void submitAudit(Long stockId);

    /** 审核通过 (1→2→3 同步库存 qtyIn/qtyAvailable) */
    void auditPass(Long stockId);

    /** 审核驳回 (1→4) */
    void auditReject(Long stockId, String rejectReason);

    /** 删除草稿/驳回的入库单 */
    void removeDoc(Long stockId);

    /* ======================= 成品出库单 ======================= */
    /** 成品出库单分页查询 (docType=OUTBOUND) */
    IPage<FasProductStock> selectOutboundPage(int pageNum, int pageSize, Integer docStatus, String docNo,
                                               String productName, String batchNo, String warehouse);

    /** 新建成品出库单 (0 草稿) */
    Long createOutboundDraft(FasProductStock stock);

    /** 成品出库单审核通过 (1→2→3 扣减 qtyOut/qtyAvailable) */
    void outboundAuditPass(Long stockId);

    /** 成品出库单驳回 */
    void outboundAuditReject(Long stockId, String rejectReason);

    /* ======================= 出入库流水 ======================= */
    IPage<FasProductStock> selectFlowPage(int pageNum, int pageSize, String docType, String sourceType,
                                           String productName, String warehouse,
                                           String beginTime, String endTime);

    /* ======================= 工具 ======================= */
    String nextInboundDocNo();
    String nextOutboundDocNo();
}
