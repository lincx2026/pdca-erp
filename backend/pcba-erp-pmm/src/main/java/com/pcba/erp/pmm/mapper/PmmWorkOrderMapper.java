package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.PmmWorkOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PmmWorkOrderMapper extends BaseMapper<PmmWorkOrder> {

    @Select("<script>" +
            "SELECT * FROM pmm_work_order " +
            "<where>" +
            "  <if test='orderNo != null and orderNo != \"\"'>" +
            "    AND order_no LIKE CONCAT('%', CAST(#{orderNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='materialCode != null and materialCode != \"\"'>" +
            "    AND material_code LIKE CONCAT('%', CAST(#{materialCode} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='materialName != null and materialName != \"\"'>" +
            "    AND material_name LIKE CONCAT('%', CAST(#{materialName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='sourceType != null and sourceType != \"\"'>" +
            "    AND source_type = #{sourceType}" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND status = #{status}" +
            "  </if>" +
            "  <if test='workCenter != null and workCenter != \"\"'>" +
            "    AND work_center LIKE CONCAT('%', CAST(#{workCenter} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='saleOrderNo != null and saleOrderNo != \"\"'>" +
            "    AND sale_order_no LIKE CONCAT('%', CAST(#{saleOrderNo} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY work_order_id DESC" +
            "</script>")
    IPage<PmmWorkOrder> selectPage(Page<PmmWorkOrder> page,
                                   @Param("orderNo") String orderNo,
                                   @Param("materialCode") String materialCode,
                                   @Param("materialName") String materialName,
                                   @Param("sourceType") String sourceType,
                                   @Param("status") Integer status,
                                   @Param("workCenter") String workCenter,
                                   @Param("saleOrderNo") String saleOrderNo);

    @Select("SELECT COUNT(*) FROM pmm_work_order WHERE order_no = #{orderNo} " +
            "AND (#{workOrderId} IS NULL OR work_order_id != #{workOrderId})")
    int countDuplicate(@Param("orderNo") String orderNo, @Param("workOrderId") Long workOrderId);
}
