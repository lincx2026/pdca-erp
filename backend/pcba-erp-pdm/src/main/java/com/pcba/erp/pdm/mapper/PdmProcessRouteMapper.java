package com.pcba.erp.pdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.pdm.entity.PdmProcessRoute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PdmProcessRouteMapper extends BaseMapper<PdmProcessRoute> {

    @Select("<script>" +
            "SELECT r.*, m.material_code AS product_code, m.material_name AS product_name, " +
            "b.bom_code AS bom_version_code " +
            "FROM pdm_process_route r " +
            "LEFT JOIN pdm_material m ON r.product_id = m.material_id " +
            "LEFT JOIN pdm_bom b ON r.bom_version_id = b.bom_id " +
            "<where> " +
            "  <if test='routeCode!=null and routeCode!=\"\"'> AND r.route_code LIKE CONCAT('%',CAST(#{routeCode} AS TEXT),'%') </if>" +
            "  <if test='routeName!=null and routeName!=\"\"'> AND r.route_name LIKE CONCAT('%',CAST(#{routeName} AS TEXT),'%') </if>" +
            "  <if test='productId!=null'> AND r.product_id = #{productId} </if>" +
            "  <if test='productCode!=null and productCode!=\"\"'> AND m.material_code LIKE CONCAT('%',CAST(#{productCode} AS TEXT),'%') </if>" +
            "  <if test='status!=null'> AND r.status = #{status} </if>" +
            "  ${dataScope} " +
            "</where> " +
            "ORDER BY r.route_id DESC" +
            "</script>")
    List<PdmProcessRoute> selectRouteList(@Param("routeCode") String routeCode,
                                           @Param("routeName") String routeName,
                                           @Param("productId") Long productId,
                                           @Param("productCode") String productCode,
                                           @Param("status") Integer status,
                                           @Param("dataScope") String dataScope);
}
