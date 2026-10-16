package com.pcba.erp.sal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.sal.entity.SalCustomerPrice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SalCustomerPriceMapper extends BaseMapper<SalCustomerPrice> {

    /** 分页查询: 支持 客户/物料/币种/状态 + 客户名称模糊 + 有效期筛选 */
    @Select("<script>" +
            "SELECT * FROM sal_customer_price " +
            "<where>" +
            "  <if test='customerId != null'> AND customer_id = #{customerId} </if>" +
            "  <if test='materialId != null'> AND material_id = #{materialId} </if>" +
            "  <if test=\"currency != null and currency != ''\"> AND currency = #{currency} </if>" +
            "  <if test='status != null'> AND status = #{status} </if>" +
            "  <if test=\"customerName != null and customerName != ''\"> AND customer_name LIKE CONCAT('%', CAST(#{customerName} AS TEXT), '%') </if>" +
            "  <if test='onlyValid != null and onlyValid == 1'> AND status = 2 AND valid_from &lt;= CURRENT_DATE AND valid_to &gt;= CURRENT_DATE </if>" +
            "</where>" +
            " ORDER BY price_id DESC" +
            "</script>")
    IPage<SalCustomerPrice> selectPage(Page<SalCustomerPrice> page,
                                       @Param("customerId") Long customerId,
                                       @Param("materialId") Long materialId,
                                       @Param("currency") String currency,
                                       @Param("status") Integer status,
                                       @Param("customerName") String customerName,
                                       @Param("onlyValid") Integer onlyValid);

    /**
     * SO下单带价: 查 客户+物料 在指定日期生效(status=2) 且 min_qty<=qty 的所有阶梯价
     * 由 Service 层按 is_default优先 / min_qty最大优先 取最优一条
     */
    @Select("SELECT * FROM sal_customer_price " +
            "WHERE customer_id = #{customerId} AND material_id = #{materialId} " +
            "  AND status = 2 " +
            "  AND valid_from <= #{date} AND valid_to >= #{date} " +
            "  AND min_qty <= #{qty} " +
            "ORDER BY is_default DESC, min_qty DESC, unit_price ASC")
    List<SalCustomerPrice> selectEffectivePrices(@Param("customerId") Long customerId,
                                                  @Param("materialId") Long materialId,
                                                  @Param("qty") BigDecimal qty,
                                                  @Param("date") LocalDate date);
}
