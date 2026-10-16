package com.pcba.erp.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmApPayableLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScmApPayableLineMapper extends BaseMapper<ScmApPayableLine> {

    @Select("<script>" +
            "SELECT * FROM scm_ap_payable_line <where>" +
            "  <if test='payableId != null'>AND payable_id = #{payableId}</if>" +
            "  <if test='materialCode != null and materialCode != \"\"'>AND material_code LIKE CONCAT('%',CAST(#{materialCode} AS TEXT),'%')</if>" +
            "  <if test='materialName != null and materialName != \"\"'>AND material_name LIKE CONCAT('%',CAST(#{materialName} AS TEXT),'%')</if>" +
            "</where> ORDER BY line_no ASC, line_id DESC</script>")
    IPage<ScmApPayableLine> selectPage(IPage<ScmApPayableLine> page,
                                       @Param("payableId") Long payableId,
                                       @Param("materialCode") String materialCode,
                                       @Param("materialName") String materialName);

    @Select("SELECT COUNT(*) FROM scm_ap_payable_line WHERE line_id <> #{id} AND 0=1")
    long countDuplicate(@Param("no") String no, @Param("id") Long id);
}
