package com.pcba.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 分页查询用户(带部门 join + 数据范围过滤) */
    @Select("<script>" +
            "SELECT DISTINCT u.*, d.dept_name, d.leader dept_leader " +
            "FROM sys_user u " +
            "LEFT JOIN sys_dept d ON u.dept_id = d.dept_id " +
            "LEFT JOIN sys_user_role ur ON u.user_id = ur.user_id " +
            "LEFT JOIN sys_role r ON ur.role_id = r.role_id " +
            "<where> u.del_flag = '0' " +
            "<if test='deptId != null'> AND (u.dept_id = #{deptId} OR d.ancestors LIKE CONCAT('%', CAST(#{deptId} AS TEXT), '%')) </if>" +
            "<if test='userName != null and userName != \"\"'> AND u.user_name LIKE CONCAT('%', CAST(#{userName} AS TEXT), '%') </if>" +
            "<if test='status != null'> AND u.status = #{status} </if>" +
            "<if test='phonenumber != null and phonenumber != \"\"'> AND u.phonenumber LIKE CONCAT('%', CAST(#{phonenumber} AS TEXT), '%') </if>" +
            "<if test='beginTime != null'> AND u.create_time &gt;= #{beginTime} </if>" +
            "<if test='endTime != null'> AND u.create_time &lt;= #{endTime} </if>" +
            "</where> " +
            "<if test='dataScopeSql != null and dataScopeSql != \"\"'> ${dataScopeSql} </if> " +
            "ORDER BY u.user_id DESC" +
            "</script>")
    IPage<SysUser> selectUserPage(IPage<SysUser> page,
                                  @Param("deptId") Long deptId,
                                  @Param("userName") String userName,
                                  @Param("status") Integer status,
                                  @Param("phonenumber") String phonenumber,
                                  @Param("beginTime") String beginTime,
                                  @Param("endTime") String endTime,
                                  @Param("dataScopeSql") String dataScopeSql);

    /** 根据用户名查询用户(登录用, 包含用户信息+部门名) */
    @Select("SELECT u.*, d.dept_name FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id=d.dept_id " +
            "WHERE u.user_name = #{username} AND u.del_flag='0' LIMIT 1")
    SysUser selectUserByUserName(String username);
}
