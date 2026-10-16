package com.pcba.erp.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.wms.entity.WmsInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;

/**
 * 库存台账 Mapper
 */
@Mapper
public interface WmsInventoryMapper extends BaseMapper<WmsInventory> {

    @Insert("INSERT INTO wms_inventory (warehouse_id, material_id, batch_no, quantity, locked_qty, available_qty, unit, status) " +
            "VALUES (#{warehouseId}, #{materialId}, COALESCE(#{batchNo}, ''), #{quantity}, 0, #{quantity}, #{unit}, 1) " +
            "ON CONFLICT (warehouse_id, material_id, batch_no) DO UPDATE SET " +
            "quantity = wms_inventory.quantity + EXCLUDED.quantity, " +
            "available_qty = wms_inventory.quantity + EXCLUDED.quantity - wms_inventory.locked_qty, " +
            "unit = COALESCE(NULLIF(wms_inventory.unit, ''), EXCLUDED.unit), update_time = CURRENT_TIMESTAMP")
    int increase(@Param("warehouseId") Long warehouseId, @Param("materialId") Long materialId,
                 @Param("batchNo") String batchNo, @Param("quantity") BigDecimal quantity, @Param("unit") String unit);

    @Update("UPDATE wms_inventory SET quantity = quantity - #{quantity}, " +
            "available_qty = quantity - #{quantity} - locked_qty, update_time = CURRENT_TIMESTAMP " +
            "WHERE warehouse_id = #{warehouseId} AND material_id = #{materialId} " +
            "AND batch_no = COALESCE(#{batchNo}, '') AND status = 1 " +
            "AND quantity - locked_qty >= #{quantity}")
    int decrease(@Param("warehouseId") Long warehouseId, @Param("materialId") Long materialId,
                 @Param("batchNo") String batchNo, @Param("quantity") BigDecimal quantity);

    /**
     * 分页查询 (联表 wms_warehouse + pdm_material)
     * 支持仓库ID/物料编码/物料名称/状态筛选
     */
    @Select("<script>" +
            "SELECT i.*, w.warehouse_name, w.warehouse_code, " +
            "       m.material_code, m.material_name, m.specification " +
            "FROM wms_inventory i " +
            "LEFT JOIN wms_warehouse w ON i.warehouse_id = w.warehouse_id " +
            "LEFT JOIN pdm_material m ON i.material_id = m.material_id " +
            "<where>" +
            "  <if test='warehouseId != null'>" +
            "    AND i.warehouse_id = #{warehouseId}" +
            "  </if>" +
            "  <if test='materialCode != null and materialCode != \"\"'>" +
            "    AND m.material_code LIKE CONCAT('%', CAST(#{materialCode} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='materialName != null and materialName != \"\"'>" +
            "    AND m.material_name LIKE CONCAT('%', CAST(#{materialName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND i.status = #{status}" +
            "  </if>" +
            "</where>" +
            " ORDER BY i.inventory_id DESC" +
            "</script>")
    IPage<WmsInventory> selectInventoryPage(Page<WmsInventory> page,
                                           @Param("warehouseId") Long warehouseId,
                                           @Param("materialCode") String materialCode,
                                           @Param("materialName") String materialName,
                                           @Param("status") Integer status);

    /** 按 仓库+物料+批次 查询库存（唯一键） */
    @Select("SELECT * FROM wms_inventory " +
            "WHERE warehouse_id = #{warehouseId} AND material_id = #{materialId} " +
            "AND batch_no = COALESCE(#{batchNo}, '') " +
            "LIMIT 1")
    WmsInventory selectByWhMatBatch(@Param("warehouseId") Long warehouseId,
                                    @Param("materialId") Long materialId,
                                    @Param("batchNo") String batchNo);
}
