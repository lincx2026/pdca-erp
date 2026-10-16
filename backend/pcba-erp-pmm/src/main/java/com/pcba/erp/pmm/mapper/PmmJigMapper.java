package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.pmm.entity.PmmJig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PmmJigMapper extends BaseMapper<PmmJig> {

    @Select("<script>" +
            "SELECT * FROM pmm_jig " +
            "<where>" +
            "  <if test='jigNo != null and jigNo != \"\"'>" +
            "    AND jig_no LIKE CONCAT('%', CAST(#{jigNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='jigName != null and jigName != \"\"'>" +
            "    AND jig_name LIKE CONCAT('%', CAST(#{jigName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='jigType != null and jigType != \"\"'>" +
            "    AND jig_type = #{jigType}" +
            "  </if>" +
            "  <if test='productCode != null and productCode != \"\"'>" +
            "    AND product_code LIKE CONCAT('%', CAST(#{productCode} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='productName != null and productName != \"\"'>" +
            "    AND product_name LIKE CONCAT('%', CAST(#{productName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND status = #{status}" +
            "  </if>" +
            "  <if test='keeper != null and keeper != \"\"'>" +
            "    AND (keeper_name LIKE CONCAT('%', CAST(#{keeper} AS TEXT), '%') OR keeper_id IS NOT NULL AND keeper_name LIKE CONCAT('%', CAST(#{keeper} AS TEXT), '%'))" +
            "  </if>" +
            "  <if test='warehouse != null and warehouse != \"\"'>" +
            "    AND warehouse LIKE CONCAT('%', CAST(#{warehouse} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='location != null and location != \"\"'>" +
            "    AND location LIKE CONCAT('%', CAST(#{location} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='borrowerName != null and borrowerName != \"\"'>" +
            "    AND borrower_name LIKE CONCAT('%', CAST(#{borrowerName} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY status ASC, CASE jig_type WHEN 'STENCIL' THEN 1 WHEN 'CARRIER' THEN 2 WHEN 'FCT_FIXTURE' THEN 3 WHEN 'ICT_FIXTURE' THEN 4 WHEN 'REFLOW_FIXTURE' THEN 5 WHEN 'ROUTER_FIXTURE' THEN 6 WHEN 'TOOL' THEN 7 ELSE 8 END, jig_no ASC" +
            "</script>")
    IPage<PmmJig> selectPage(Page<PmmJig> page,
                              @Param("jigNo")        String jigNo,
                              @Param("jigName")      String jigName,
                              @Param("jigType")      String jigType,
                              @Param("productCode")  String productCode,
                              @Param("productName")  String productName,
                              @Param("status")       Integer status,
                              @Param("keeper")       String keeper,
                              @Param("warehouse")    String warehouse,
                              @Param("location")     String location,
                              @Param("borrowerName") String borrowerName);

    @Select("SELECT COUNT(*) FROM pmm_jig WHERE jig_no = #{jigNo} AND (#{excludeId} IS NULL OR jig_id != #{excludeId})")
    long countDuplicateNo(@Param("jigNo") String jigNo, @Param("excludeId") Long excludeId);

    @Select("<script>" +
            "SELECT * FROM pmm_jig WHERE status IN (0,1,2,3,4,5) " +
            "<if test='productCode != null and productCode != \"\"'>" +
            "  AND product_code = #{productCode}" +
            "</if>" +
            " ORDER BY jig_no ASC" +
            "</script>")
    List<PmmJig> listOptions(@Param("productCode") String productCode);
}
