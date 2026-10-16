package com.pcba.erp.sal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalKitting;

import java.util.List;

/**
 * 销售齐套 Service
 * - 齐套单 CRUD + 状态机
 * - 核心动作: 齐套检查(展开BOM×套数→查库存) / 生成出库 / 生成请购
 */
public interface ISalKittingService {

    IPage<SalKitting> selectPage(int pageNum, int pageSize,
                                 String kittingNo, String soNo,
                                 Long customerId, Integer status, Long bomId);

    SalKitting getDetail(Long id);

    Long save(SalKitting entity);

    boolean update(SalKitting entity);

    boolean remove(Long id);

    List<SalKitting> options();

    String nextKittingNo();

    /** 齐套检查: 展开 BOM×kitQty → 查库存 → 回写明细 req/available/shortage → 推进状态 */
    boolean runKittingCheck(Long kittingId);

    /** 齐套完成: 校验无缺料 → 生成 WMS 出库单 + 回填 stockOutId → status→3 */
    Long generateStockOut(Long kittingId);

    /** 生成请购单: 缺料明细 → SCM 请购主+行 → 回填 requisitionId → status→4 */
    Long generatePurchaseRequisition(Long kittingId);

    boolean cancelKitting(Long id);

    /**
     * P0: 从销售订单行创建销售齐套单（自动匹配销售BOM并立即执行齐套检查）
     *
     * @param soLineId      销售订单行ID (sal_sales_order_line.line_id)
     * @param bomIdOverride 可选: 强制指定BOM, 为null时按 customerId+productMaterialId 自动匹配 bomType=3 & status=2 的销售BOM
     * @return 齐套单详情（含明细 + 齐套检查结果汇总）
     */
    SalKitting createFromOrderLine(Long soLineId, Long bomIdOverride);
}
