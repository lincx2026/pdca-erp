package com.pcba.erp.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.wms.entity.WmsStockInLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 入库单明细 Mapper
 */
@Mapper
public interface WmsStockInLineMapper extends BaseMapper<WmsStockInLine> {

    /** 按入库单ID查询所有明细 */
    @Select("SELECT * FROM wms_stock_in_line WHERE stock_in_id = #{stockInId} ORDER BY line_id ASC")
    List<WmsStockInLine> selectByStockInId(@Param("stockInId") Long stockInId);
}