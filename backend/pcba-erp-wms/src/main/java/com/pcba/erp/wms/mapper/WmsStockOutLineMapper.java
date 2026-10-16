package com.pcba.erp.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.wms.entity.WmsStockOutLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 出库单明细 Mapper
 */
@Mapper
public interface WmsStockOutLineMapper extends BaseMapper<WmsStockOutLine> {

    /** 按出库单ID查询所有明细 */
    @Select("SELECT * FROM wms_stock_out_line WHERE stock_out_id = #{stockOutId} ORDER BY line_id ASC")
    List<WmsStockOutLine> selectByStockOutId(@Param("stockOutId") Long stockOutId);
}