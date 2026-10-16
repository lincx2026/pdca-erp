package com.pcba.erp.sal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.sal.entity.SalSalesReturnLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalSalesReturnLineMapper extends BaseMapper<SalSalesReturnLine> {

    @Select("<script>" +
            "SELECT * FROM sal_sales_return_line " +
            "<where>" +
            "  <if test='returnId != null'>" +
            "    AND return_id = #{returnId}" +
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
    IPage<SalSalesReturnLine> selectPage(Page<SalSalesReturnLine> page, @Param("returnId") Long returnId, @Param("materialCode") String materialCode, @Param("materialName") String materialName);
}
