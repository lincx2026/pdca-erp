package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.HrmEmployee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface HrmEmployeeMapper extends BaseMapper<HrmEmployee> {

    @Select("<script>" +
            "SELECT e.*, d.dept_name AS \"deptName\" FROM hrm_employee e " +
            "LEFT JOIN sys_dept d ON e.dept_id = d.dept_id " +
            "<where>" +
            "  <if test='employeeNo != null and employeeNo != \"\"'> AND e.employee_no LIKE CONCAT('%', CAST(#{employeeNo} AS TEXT), '%') </if>" +
            "  <if test='name != null and name != \"\"'> AND e.name LIKE CONCAT('%', CAST(#{name} AS TEXT), '%') </if>" +
            "  <if test='phone != null and phone != \"\"'> AND e.phone LIKE CONCAT('%', CAST(#{phone} AS TEXT), '%') </if>" +
            "  <if test='deptId != null'> AND e.dept_id = #{deptId} </if>" +
            "  <if test='status != null'> AND e.status = #{status} </if>" +
            "</where>" +
            " ORDER BY e.employee_no ASC" +
            "</script>")
    IPage<HrmEmployee> selectPage(Page<HrmEmployee> page,
                                  @Param("employeeNo") String employeeNo,
                                  @Param("name") String name,
                                  @Param("phone") String phone,
                                  @Param("deptId") Long deptId,
                                  @Param("status") Integer status);

    /** 查询所有部门(树形结构由 Service 组装) */
    @Select("SELECT dept_id AS \"deptId\", parent_id AS \"parentId\", dept_name AS \"deptName\", order_num AS \"orderNum\" " +
            "FROM sys_dept WHERE status = 1 AND del_flag = 0 ORDER BY order_num ASC, dept_id ASC")
    List<Map<String, Object>> selectDeptList();
}
