package com.pcba.erp.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.wms.entity.WmsStockIn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 入库单 Mapper
 */
@Mapper
public interface WmsStockInMapper extends BaseMapper<WmsStockIn> {

    @Select("SELECT * FROM wms_stock_in WHERE stock_in_id = #{id} FOR UPDATE")
    WmsStockIn selectForUpdate(@Param("id") Long id);

    /** 联表 wms_warehouse 的分页查询，回填 warehouseName */
    @Select("<script>" +
            "SELECT s.*, w.warehouse_name AS warehouse_name " +
            "FROM wms_stock_in s " +
            "LEFT JOIN wms_warehouse w ON s.warehouse_id = w.warehouse_id " +
            "<where> " +
            "  <if test='orderNo!=null and orderNo!=\"\"'> AND s.order_no LIKE CONCAT('%',CAST(#{orderNo} AS TEXT),'%') </if>" +
            "  <if test='warehouseId!=null'> AND s.warehouse_id = #{warehouseId} </if>" +
            "  <if test='inType!=null and inType!=\"\"'> AND s.in_type = #{inType} </if>" +
            "  <if test='status!=null'> AND s.status = #{status} </if>" +
            "  ${dataScope} " +
            "</where> " +
            "ORDER BY s.stock_in_id DESC" +
            "</script>")
    List<WmsStockIn> selectStockInPage(@Param("orderNo") String orderNo,
                                       @Param("warehouseId") Long warehouseId,
                                       @Param("inType") String inType,
                                       @Param("status") Integer status,
                                       @Param("dataScope") String dataScope);

    /** 查询某前缀下最大的入库单号（用于生成序号） */
    @Select("SELECT MAX(order_no) FROM wms_stock_in WHERE order_no LIKE CONCAT(CAST(#{prefix} AS TEXT), '%')")
    String selectMaxOrderNoByPrefix(@Param("prefix") String prefix);
}
