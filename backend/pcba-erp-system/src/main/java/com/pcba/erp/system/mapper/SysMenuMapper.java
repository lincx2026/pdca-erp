package com.pcba.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /** 超级管理员查询所有菜单（除按钮，显示正常的） */
    @Select("SELECT DISTINCT m.* FROM sys_menu m " +
            "WHERE m.visible = 0 AND m.status = 0 AND m.menu_type IN ('M','C') " +
            "ORDER BY m.parent_id, m.order_num")
    List<SysMenu> selectMenuTreeAll();

    /** 普通用户按角色查菜单树（M、C）。注意 status=1=正常（与 SysRoleMapper.selectRolesByUserId 语义一致） */
    @Select("SELECT m.* FROM sys_menu m " +
            "INNER JOIN sys_role_menu rm ON m.menu_id = rm.menu_id " +
            "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "INNER JOIN sys_role r ON ur.role_id = r.role_id " +
            "WHERE ur.user_id = #{userId} AND m.visible = 0 AND m.status = 0 " +
            "AND r.status = 1 AND m.menu_type IN ('M','C') " +
            "GROUP BY m.menu_id " +
            "ORDER BY m.parent_id, m.order_num")
    List<SysMenu> selectMenuTreeByUserId(Long userId);

    /** 普通用户按角色查所有 perms（F 按钮级权限）。status=1=正常 */
    @Select("SELECT DISTINCT m.perms FROM sys_menu m " +
            "INNER JOIN sys_role_menu rm ON m.menu_id = rm.menu_id " +
            "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "INNER JOIN sys_role r ON ur.role_id = r.role_id " +
            "WHERE ur.user_id = #{userId} AND m.perms IS NOT NULL AND m.perms <> '' AND r.status = 1")
    List<String> selectMenuPermsByUserId(Long userId);

    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(Long roleId);

    /** 菜单管理页查询：保留 M/C/F 全类型，按名称和状态筛选 */
    @Select("<script>" +
            "SELECT * FROM sys_menu " +
            "<where>" +
            "<if test='menuName != null and menuName != \"\"'> AND menu_name LIKE CONCAT('%', CAST(#{menuName} AS TEXT), '%') </if>" +
            "<if test='status != null'> AND status = #{status} </if>" +
            "</where> ORDER BY parent_id, order_num" +
            "</script>")
    List<SysMenu> selectMenuList(@Param("menuName") String menuName, @Param("status") Integer status);

    /** excludeTree：在新增/修改菜单时，排除自身及后代节点，避免循环依赖 */
    @Select("<script>" +
            "SELECT * FROM sys_menu WHERE menu_type IN ('M') " +
            "<if test='excludeId != null'> AND menu_id NOT IN " +
            "  (WITH RECURSIVE menu_tree AS " +
            "   (SELECT menu_id FROM sys_menu WHERE menu_id = #{excludeId} " +
            "    UNION ALL " +
            "    SELECT m.menu_id FROM sys_menu m INNER JOIN menu_tree t ON m.parent_id = t.menu_id) " +
            "   SELECT menu_id FROM menu_tree)" +
            "</if> " +
            "ORDER BY parent_id, order_num" +
            "</script>")
    List<SysMenu> selectMenuTreeExclude(@Param("excludeId") Long excludeId);
}
