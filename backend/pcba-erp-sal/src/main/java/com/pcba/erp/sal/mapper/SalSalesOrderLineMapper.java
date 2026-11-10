package com.pcba.erp.sal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.sal.entity.SalSalesOrderLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalSalesOrderLineMapper extends BaseMapper<SalSalesOrderLine> {

    @Select("<script>SELECT DISTINCT ON (so_id) so_id AS \"soId\", material_name AS \"materialName\", specification, COUNT(*) OVER (PARTITION BY so_id) AS \"lineCount\", COUNT(line_amount) OVER (PARTITION BY so_id) AS \"amountCount\", SUM(COALESCE(line_amount,0)) OVER (PARTITION BY so_id) AS \"lineAmountTotal\" FROM sal_sales_order_line WHERE so_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY so_id,line_no,line_id</script>")
    java.util.List<java.util.Map<String,Object>> firstLines(@Param("ids") java.util.List<Long> ids);

    @Select("<script>SELECT so_id AS \"soId\", COALESCE(NULLIF(TRIM(unit),''),'未填写单位') AS unit, SUM(order_qty) AS quantity FROM sal_sales_order_line WHERE so_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY so_id,COALESCE(NULLIF(TRIM(unit),''),'未填写单位') ORDER BY so_id,unit</script>")
    java.util.List<java.util.Map<String,Object>> quantitiesByUnit(@Param("ids") java.util.List<Long> ids);

    @Select("<script>" +
            "SELECT * FROM sal_sales_order_line " +
            "<where>" +
            "  <if test='soId != null'>" +
            "    AND so_id = #{soId}" +
            "  </if>" +
            "  <if test=\"materialCode != null and materialCode != ''\">" +
            "    AND material_code LIKE CONCAT('%', CAST(#{materialCode} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"materialName != null and materialName != ''\">" +
            "    AND material_name LIKE CONCAT('%', CAST(#{materialName} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY line_id DESC" +
            "</script>")
    IPage<SalSalesOrderLine> selectPage(Page<SalSalesOrderLine> page, @Param("soId") Long soId, @Param("materialCode") String materialCode, @Param("materialName") String materialName);
}
