package com.pcba.erp.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.wms.entity.WmsStockOut;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 出库单 Mapper
 */
@Mapper
public interface WmsStockOutMapper extends BaseMapper<WmsStockOut> {

    @Select("SELECT * FROM wms_stock_out WHERE stock_out_id = #{id} FOR UPDATE")
    WmsStockOut selectForUpdate(@Param("id") Long id);

    /**
     * 分页查询出库单，联表 wms_warehouse 回填 warehouseName
     * 支持按 orderNo LIKE / warehouseId = / outType = / status = 筛选
     */
    @Select("<script>" +
            "SELECT s.*, w.warehouse_name AS warehouse_name " +
            "FROM wms_stock_out s " +
            "LEFT JOIN wms_warehouse w ON s.warehouse_id = w.warehouse_id " +
            "<where> " +
            "  <if test='orderNo != null and orderNo != \"\"'> " +
            "    AND s.order_no LIKE CONCAT('%', CAST(#{orderNo} AS TEXT), '%') " +
            "  </if> " +
            "  <if test='warehouseId != null'> AND s.warehouse_id = #{warehouseId} </if> " +
            "  <if test='outType != null and outType != \"\"'> AND s.out_type = #{outType} </if> " +
            "  <if test='status != null'> AND s.status = #{status} </if> " +
            "</where> " +
            "ORDER BY s.stock_out_id DESC" +
            "</script>")
    IPage<WmsStockOut> selectStockOutPage(Page<WmsStockOut> page,
                                          @Param("orderNo") String orderNo,
                                          @Param("warehouseId") Long warehouseId,
                                          @Param("outType") String outType,
                                          @Param("status") Integer status);
}
