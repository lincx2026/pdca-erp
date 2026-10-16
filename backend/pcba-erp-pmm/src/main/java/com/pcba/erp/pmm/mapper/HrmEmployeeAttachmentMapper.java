package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.pmm.entity.HrmEmployeeAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HrmEmployeeAttachmentMapper extends BaseMapper<HrmEmployeeAttachment> {

    @Select("SELECT * FROM hrm_employee_attachment WHERE employee_id = #{employeeId} ORDER BY create_time DESC")
    List<HrmEmployeeAttachment> selectByEmployeeId(@Param("employeeId") Long employeeId);
}
