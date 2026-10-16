package com.pcba.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.system.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /** 查询部门列表(带数据范围过滤) */
    @Select("<script>" +
            "SELECT d.*, (SELECT dept_name FROM sys_dept p WHERE p.dept_id = d.parent_id) parent_name " +
            "FROM sys_dept d " +
            "<where> d.del_flag = '0' " +
            "<if test='deptName != null and deptName != \"\"'> AND d.dept_name LIKE CONCAT('%', CAST(#{deptName} AS TEXT), '%') </if>" +
            "<if test='status != null'> AND d.status = #{status} </if>" +
            "</where> " +
            "<if test='dataScopeSql != null and dataScopeSql != \"\"'> ${dataScopeSql} </if> " +
            "ORDER BY d.parent_id, d.order_num" +
            "</script>")
    List<SysDept> selectDeptList(@Param("deptName") String deptName,
                                  @Param("status") Integer status,
                                  @Param("dataScopeSql") String dataScopeSql);

    /** 根据角色ID查询部门树(给自定义数据范围下拉选) */
    @Select("SELECT DISTINCT d.dept_id, d.parent_id, d.dept_name, d.order_num " +
            "FROM sys_dept d " +
            "LEFT JOIN sys_role_dept rd ON d.dept_id = rd.dept_id " +
            "WHERE d.del_flag = '0' AND (rd.role_id = #{roleId} OR d.dept_id = 100) " +
            "ORDER BY d.parent_id, d.order_num")
    List<SysDept> selectDeptListByRoleId(Long roleId);
}
