package com.pcba.erp.sal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalForecast;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 销售预测/滚动计划 Service
 * 状态机: 0草稿 → 1生效 → 2已归档
 * 算法: 移动平均(MA-8) SQL AVG, 历史不足跳过避免噪音
 */
public interface ISalForecastService {
    IPage<SalForecast> selectPage(int pageNum, int pageSize, String versionNo, String versionType,
                                    Long customerId, Long materialId, Integer status,
                                    String customerName, String materialName);
    SalForecast getDetail(Long id);
    Long save(SalForecast entity);
    boolean update(SalForecast entity);
    boolean remove(Long id);
    List<Map<String, Object>> listVersions();

    /**
     * 生成新版本(滚动计划核心)
     *  - 回写上一生效版本已过去周期的 actual_qty/hit_rate
     *  - 旧生效版本 1→2 归档(保留历史,不删)
     *  - 按"过去4周有订单"的客户+物料组合生成未来 N 周预测行
     *  - MA-8算法: 过去8周实际订单量 / 8 = 周平均预测量
     *  - 历史不足(totalQty<=0)的维度跳过避免噪音
     *  - 新版本默认 0 草稿,需手动 audit 推进 0→1 生效
     * @param type W=周版本 / M=月版本
     * @param periodsAhead 生成未来N个周期(默认12)
     * @param operator 操作人
     * @return 生成的预测行数
     */
    int generateVersion(String type, int periodsAhead, String operator);

    /** 回写已过去周期的实际订单量+命中率 */
    int rollbackActual(String versionNo);

    /** 版本审核生效 0→1 */
    boolean auditVersion(String versionNo, String operator);

    /** 版本归档 1→2 */
    boolean archiveVersion(String versionNo);

    /** 手工调整预测量(仅草稿可改,自动重算金额=qty×客户专属价) */
    boolean adjustQty(Long forecastId, BigDecimal newQty, String operator);

    // ============ 看板 ============
    /** 看板汇总: 当前生效版本的总预测/实际/命中率 */
    Map<String, Object> kanbanSummary(Long customerId);
    /** 看板趋势: 当前生效版本按周期分桶(预测vs实际) */
    List<Map<String, Object>> forecastTrend(Long customerId, int periods);
    /** 看板Top客户(按预测金额降序) */
    List<Map<String, Object>> topCustomers(Long customerId, int limit);
    /** 看板Top物料(按预测数量降序) */
    List<Map<String, Object>> topMaterials(Long customerId, int limit);
}
