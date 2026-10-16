package com.pcba.erp.sal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.sal.entity.SalForecast;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 销售预测/滚动计划 Mapper
 * 注: 纯字符串SQL用字面 <>/>/!=, 不要写 &lt;/&gt; 实体(踩坑已修正)
 */
@Mapper
public interface SalForecastMapper extends BaseMapper<SalForecast> {

    /** 分页查询: 支持版本号/客户/物料/状态 + 客户名称模糊 */
    @Select("<script>" +
            "SELECT * FROM sal_forecast " +
            "<where>" +
            "  <if test=\"versionNo != null and versionNo != ''\"> AND version_no = #{versionNo} </if>" +
            "  <if test='versionType != null'> AND version_type = #{versionType} </if>" +
            "  <if test='customerId != null'> AND customer_id = #{customerId} </if>" +
            "  <if test='materialId != null'> AND material_id = #{materialId} </if>" +
            "  <if test='status != null'> AND status = #{status} </if>" +
            "  <if test=\"customerName != null and customerName != ''\"> AND customer_name LIKE CONCAT('%', CAST(#{customerName} AS TEXT), '%') </if>" +
            "  <if test=\"materialName != null and materialName != ''\"> AND material_name LIKE CONCAT('%', CAST(#{materialName} AS TEXT), '%') </if>" +
            "</where>" +
            " ORDER BY period_start ASC, forecast_id DESC" +
            "</script>")
    IPage<SalForecast> selectPage(Page<SalForecast> page,
                                  @Param("versionNo") String versionNo,
                                  @Param("versionType") String versionType,
                                  @Param("customerId") Long customerId,
                                  @Param("materialId") Long materialId,
                                  @Param("status") Integer status,
                                  @Param("customerName") String customerName,
                                  @Param("materialName") String materialName);

    /** 列出所有版本号(去重,用于版本切换下拉) */
    @Select("SELECT DISTINCT version_no AS \"versionNo\", version_type AS \"versionType\", " +
            "MIN(period_start) AS \"periodStart\", MAX(period_end) AS \"periodEnd\", " +
            "MAX(status) AS status " +
            "FROM sal_forecast GROUP BY version_no, version_type ORDER BY version_no DESC")
    List<Map<String, Object>> listVersions();

    /** 取指定版本的最大状态(判断是草稿/生效/归档) */
    @Select("SELECT MAX(status) FROM sal_forecast WHERE version_no = #{versionNo}")
    Integer selectVersionStatus(@Param("versionNo") String versionNo);

    /** 查客户+物料过去8周实际订单量(用于MA-8移动平均预测) - so表PK=so_id, 日期=so_date */
    @Select("SELECT COALESCE(SUM(sol.order_qty), 0) AS qty " +
            "FROM sal_sales_order_line sol " +
            "INNER JOIN sal_sales_order so ON sol.so_id = so.so_id " +
            "WHERE so.status <> 9 " +
            "  AND (so.customer_id = #{customerId} OR CAST(#{customerId} AS BIGINT) IS NULL) " +
            "  AND sol.material_id = #{materialId} " +
            "  AND so.so_date BETWEEN #{dateFrom} AND #{dateTo}")
    BigDecimal sumOrderQty(@Param("customerId") Long customerId,
                            @Param("materialId") Long materialId,
                            @Param("dateFrom") LocalDate dateFrom,
                            @Param("dateTo") LocalDate dateTo);

    /** 取客户专属价(用于预测金额计算) */
    @Select("SELECT unit_price FROM sal_customer_price " +
            "WHERE customer_id = #{customerId} AND material_id = #{materialId} " +
            "  AND status = 2 AND valid_from <= CURRENT_DATE AND valid_to >= CURRENT_DATE " +
            "ORDER BY is_default DESC, min_qty DESC, unit_price ASC LIMIT 1")
    BigDecimal selectCustomerPrice(@Param("customerId") Long customerId,
                                    @Param("materialId") Long materialId);

    /** 找出过去4周有订单的客户+物料组合(用于预测维度生成) - so表PK=so_id, 日期=so_date */
    @Select("SELECT DISTINCT so.customer_id AS \"customerId\", MAX(so.customer_name) AS \"customerName\", " +
            "sol.material_id AS \"materialId\", MAX(sol.material_code) AS \"materialCode\", " +
            "MAX(sol.material_name) AS \"materialName\", MAX(sol.unit) AS unit " +
            "FROM sal_sales_order_line sol " +
            "INNER JOIN sal_sales_order so ON sol.so_id = so.so_id " +
            "WHERE so.status <> 9 AND so.so_date >= #{since} " +
            "GROUP BY so.customer_id, sol.material_id")
    List<Map<String, Object>> listRecentCustomerMaterials(@Param("since") LocalDate since);

    /** 取某版本未来12周的所有周期段(用于批量插入) */
    @Select("SELECT * FROM sal_forecast WHERE version_no = #{versionNo} " +
            "ORDER BY period_start ASC, customer_id, material_id")
    List<SalForecast> listByVersion(@Param("versionNo") String versionNo);

    /** 批量回写某版本已过去周期的实际订单量+命中率 */
    @Update("UPDATE sal_forecast " +
            "SET actual_qty = #{actualQty}, actual_amt = #{actualAmt}, " +
            "    hit_rate = CASE WHEN forecast_qty > 0 THEN #{actualQty} / forecast_qty ELSE 0 END, " +
            "    update_time = NOW() " +
            "WHERE forecast_id = #{forecastId}")
    int updateActual(@Param("forecastId") Long forecastId,
                      @Param("actualQty") BigDecimal actualQty,
                      @Param("actualAmt") BigDecimal actualAmt);

    /** 查所有版本号(定时任务滚动时找上一版本) */
    @Select("SELECT version_no AS \"versionNo\" FROM sal_forecast " +
            "WHERE version_type = #{type} AND status = 1 " +
            "GROUP BY version_no ORDER BY version_no DESC LIMIT 1")
    String selectLastActiveVersion(@Param("type") String type);
}
