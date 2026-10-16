package com.pcba.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.system.entity.SysRoleDept;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysRoleDeptMapper extends BaseMapper<SysRoleDept> {

    @Insert("<script>" +
            "INSERT INTO sys_role_dept(role_id, dept_id) VALUES " +
            "<foreach item='did' collection='deptIds' separator=','>(#{roleId}, #{did})</foreach>" +
            "</script>")
    int batchInsert(@Param("roleId") Long roleId, @Param("deptIds") List<Long> deptIds);

    @Delete("DELETE FROM sys_role_dept WHERE role_id = #{roleId}")
    int deleteByRoleId(Long roleId);
}
