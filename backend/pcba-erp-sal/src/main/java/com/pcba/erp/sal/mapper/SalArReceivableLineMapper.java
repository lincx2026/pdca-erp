package com.pcba.erp.sal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalArReceivableLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalArReceivableLineMapper extends BaseMapper<SalArReceivableLine> {

    @Select("<script>" +
            "SELECT * FROM sal_ar_receivable_line <where>" +
            "  <if test='receivableId != null'>AND receivable_id = #{receivableId}</if>" +
            "  <if test='materialCode != null and materialCode != \"\"'>AND material_code LIKE CONCAT('%',CAST(#{materialCode} AS TEXT),'%')</if>" +
            "  <if test='materialName != null and materialName != \"\"'>AND material_name LIKE CONCAT('%',CAST(#{materialName} AS TEXT),'%')</if>" +
            "</where> ORDER BY line_no ASC, line_id DESC</script>")
    IPage<SalArReceivableLine> selectPage(IPage<SalArReceivableLine> page,
                                          @Param("receivableId") Long receivableId,
                                          @Param("materialCode") String materialCode,
                                          @Param("materialName") String materialName);

    @Select("SELECT COUNT(*) FROM sal_ar_receivable_line WHERE line_id <> #{id} AND 0=1")
    long countDuplicate(@Param("no") String no, @Param("id") Long id);
}
