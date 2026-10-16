package com.pcba.erp.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.wms.entity.WmsWarehouse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 仓库主档 Mapper
 */
@Mapper
public interface WmsWarehouseMapper extends BaseMapper<WmsWarehouse> {

    /**
     * 分页查询 (动态条件: 仓库名称模糊 / 仓库类型 / 状态)
     */
    @Select("<script>" +
            "SELECT * FROM wms_warehouse " +
            "<where>" +
            "  <if test='warehouseName != null and warehouseName != \"\"'>" +
            "    AND warehouse_name LIKE CONCAT('%', CAST(#{warehouseName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='warehouseType != null and warehouseType != \"\"'>" +
            "    AND warehouse_type = #{warehouseType}" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND status = #{status}" +
            "  </if>" +
            "</where>" +
            " ORDER BY warehouse_id DESC" +
            "</script>")
    IPage<WmsWarehouse> selectWarehousePage(Page<WmsWarehouse> page,
                                           @Param("warehouseName") String warehouseName,
                                           @Param("warehouseType") String warehouseType,
                                           @Param("status") Integer status);

    /** 仓库编码全局唯一校验 */
    @Select("SELECT COUNT(*) FROM wms_warehouse WHERE warehouse_code = #{code} " +
            "AND (#{warehouseId} IS NULL OR warehouse_id != #{warehouseId})")
    int countDuplicateCode(@Param("code") String code, @Param("warehouseId") Long warehouseId);
}
