package com.pcba.erp.sal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.sal.entity.SalKittingLine;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 销售齐套明细 Mapper
 */
@Mapper
public interface SalKittingLineMapper extends BaseMapper<SalKittingLine> {

    default List<SalKittingLine> selectByKittingId(Long kittingId) {
        return selectList(new LambdaQueryWrapper<SalKittingLine>()
                .eq(SalKittingLine::getKittingId, kittingId)
                .orderByAsc(SalKittingLine::getLineNo));
    }

    default int deleteByKittingId(Long kittingId) {
        return delete(new LambdaQueryWrapper<SalKittingLine>()
                .eq(SalKittingLine::getKittingId, kittingId));
    }
}
