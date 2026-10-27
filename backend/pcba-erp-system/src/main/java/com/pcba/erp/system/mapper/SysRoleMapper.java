package com.pcba.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("<script>" +
            "SELECT r.* FROM sys_role r " +
            "<where> r.del_flag = '0' " +
            "<if test='roleName != null and roleName != \"\"'> AND r.role_name LIKE CONCAT('%', CAST(#{roleName} AS TEXT), '%') </if>" +
            "<if test='roleKey != null and roleKey != \"\"'> AND r.role_key LIKE CONCAT('%', CAST(#{roleKey} AS TEXT), '%') </if>" +
            "<if test='status != null'> AND r.status = #{status} </if>" +
            "<if test='beginTime != null'> AND r.create_time &gt;= #{beginTime} </if>" +
            "<if test='endTime != null'> AND r.create_time &lt;= #{endTime} </if>" +
            "</where> ORDER BY r.role_sort" +
            "</script>")
    IPage<SysRole> selectRolePage(IPage<SysRole> page,
                                  @Param("roleName") String roleName,
                                  @Param("roleKey") String roleKey,
                                  @Param("status") Integer status,
                                  @Param("beginTime") String beginTime,
                                  @Param("endTime") String endTime);

    @Select("SELECT DISTINCT r.* FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.role_id = ur.role_id " +
            "INNER JOIN sys_user u ON u.user_id = ur.user_id " +
            "WHERE u.user_id = #{userId} AND r.del_flag = '0' AND r.status = 1 " +
            "ORDER BY r.role_sort")
    List<SysRole> selectRolesByUserId(Long userId);

    @Select("SELECT DISTINCT p.perms FROM sys_menu p " +
            "INNER JOIN sys_role_menu rm ON p.menu_id = rm.menu_id " +
            "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "INNER JOIN sys_role r ON r.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1 AND r.del_flag = '0' " +
            "AND p.perms IS NOT NULL AND p.perms <> ''")
    List<String> selectPermsByUserId(Long userId);
}
