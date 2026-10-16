package com.pcba.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.system.entity.SysNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

@Mapper
public interface SysNoticeMapper extends BaseMapper<SysNotice> {

    /** 管理端分页列表：带已读/未读人数（不做接收范围过滤） */
    @Select("<script>" +
            "SELECT n.*," +
            " (SELECT COUNT(1) FROM sys_notice_read nr WHERE nr.notice_id=n.notice_id AND nr.is_read=1) AS \"readCount\"," +
            " CASE n.receiver_type WHEN 1 THEN (SELECT COUNT(1) FROM sys_user u WHERE u.status=1)" +
            "        WHEN 2 THEN (SELECT COUNT(DISTINCT ur.user_id) FROM sys_user_role ur, sys_user u" +
            "                      WHERE u.user_id=ur.user_id AND u.status=1" +
            "                      AND (CAST(ur.role_id AS TEXT) = ANY(string_to_array(n.receiver_roles, ','))))" +
            "        ELSE GREATEST(0, LENGTH(n.receiver_users) - LENGTH(REPLACE(n.receiver_users, ',', '')) + 1)" +
            " END -" +
            " (SELECT COUNT(1) FROM sys_notice_read nr WHERE nr.notice_id=n.notice_id AND nr.is_read=1) AS \"unreadCount\"" +
            " FROM sys_notice n WHERE 1=1" +
            "<if test='noticeType!=null'> AND n.notice_type = #{noticeType}</if>" +
            "<if test='noticeLevel!=null'> AND n.notice_level = #{noticeLevel}</if>" +
            "<if test='sendStatus!=null'> AND n.send_status = #{sendStatus}</if>" +
            "<if test='title!=null and title!=\"\"'> AND n.title LIKE CONCAT('%', CAST(#{title} AS TEXT), '%')</if>" +
            " ORDER BY n.is_top DESC, n.send_time DESC, n.notice_id DESC" +
            " LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<SysNotice> selectPage(@Param("offset") int offset,
                               @Param("limit") int limit,
                               @Param("noticeType") Integer noticeType,
                               @Param("noticeLevel") Integer noticeLevel,
                               @Param("sendStatus") Integer sendStatus,
                               @Param("title") String title);

    @Select("<script>" +
            "SELECT COUNT(1) FROM sys_notice n WHERE 1=1" +
            "<if test='noticeType!=null'> AND n.notice_type = #{noticeType}</if>" +
            "<if test='noticeLevel!=null'> AND n.notice_level = #{noticeLevel}</if>" +
            "<if test='sendStatus!=null'> AND n.send_status = #{sendStatus}</if>" +
            "<if test='title!=null and title!=\"\"'> AND n.title LIKE CONCAT('%', CAST(#{title} AS TEXT), '%')</if>" +
            "</script>")
    long count(@Param("noticeType") Integer noticeType,
               @Param("noticeLevel") Integer noticeLevel,
               @Param("sendStatus") Integer sendStatus,
               @Param("title") String title);

    /**
     * 用户端分页列表（只看已发布且接收范围内）
     *   receiver_type=1 全体可见；
     *   receiver_type=2 用户角色命中；
     *   receiver_type=3 用户ID命中；
     *   并且生效时间范围内；
     *   不是撤回；
     */
    @Select("<script>" +
            "SELECT n.*," +
            " COALESCE((SELECT nr.is_read FROM sys_notice_read nr WHERE nr.notice_id=n.notice_id AND nr.user_id=#{userId}), 0) AS \"readFlag\"" +
            " FROM sys_notice n WHERE n.send_status=1" +
            " AND (n.start_time IS NULL OR n.start_time&lt;=NOW())" +
            " AND (n.end_time   IS NULL OR n.end_time  &gt;=NOW())" +
            " AND (n.receiver_type=1" +
            "   OR (n.receiver_type=3 AND (CAST(#{userId} AS TEXT) = ANY(string_to_array(n.receiver_users, ','))))" +
            "   OR (n.receiver_type=2" +
            "      AND EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id=#{userId}" +
            "               AND (CAST(ur.role_id AS TEXT) = ANY(string_to_array(n.receiver_roles, ',')))))" +
            " )" +
            "<if test='noticeType!=null'> AND n.notice_type = #{noticeType}</if>" +
            "<if test='noticeLevel!=null'> AND n.notice_level = #{noticeLevel}</if>" +
            "<if test='isRead != null'>" +
            "  AND COALESCE((SELECT nr.is_read FROM sys_notice_read nr WHERE nr.notice_id=n.notice_id AND nr.user_id=#{userId}), 0) = #{isRead}" +
            "</if>" +
            "<if test='keyword!=null and keyword!=\"\"'> AND (n.title LIKE CONCAT('%', CAST(#{keyword} AS TEXT), '%') OR n.summary LIKE CONCAT('%', CAST(#{keyword} AS TEXT), '%'))</if>" +
            " ORDER BY n.is_top DESC, n.send_time DESC, n.notice_id DESC" +
            " LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<SysNotice> selectUserPage(@Param("offset") int offset,
                                   @Param("limit") int limit,
                                   @Param("userId") Long userId,
                                   @Param("noticeType") Integer noticeType,
                                   @Param("noticeLevel") Integer noticeLevel,
                                   @Param("isRead") Integer isRead,
                                   @Param("keyword") String keyword);

    @Select("<script>" +
            "SELECT COUNT(1) FROM sys_notice n WHERE n.send_status=1" +
            " AND (n.start_time IS NULL OR n.start_time&lt;=NOW())" +
            " AND (n.end_time   IS NULL OR n.end_time  &gt;=NOW())" +
            " AND (n.receiver_type=1" +
            "   OR (n.receiver_type=3 AND (CAST(#{userId} AS TEXT) = ANY(string_to_array(n.receiver_users, ','))))" +
            "   OR (n.receiver_type=2" +
            "      AND EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id=#{userId}" +
            "               AND (CAST(ur.role_id AS TEXT) = ANY(string_to_array(n.receiver_roles, ',')))))" +
            " )" +
            "<if test='noticeType!=null'> AND n.notice_type = #{noticeType}</if>" +
            "<if test='noticeLevel!=null'> AND n.notice_level = #{noticeLevel}</if>" +
            "<if test='isRead != null'>" +
            "  AND COALESCE((SELECT nr.is_read FROM sys_notice_read nr WHERE nr.notice_id=n.notice_id AND nr.user_id=#{userId}), 0) = #{isRead}" +
            "</if>" +
            "<if test='keyword!=null and keyword!=\"\"'> AND (n.title LIKE CONCAT('%', CAST(#{keyword} AS TEXT), '%') OR n.summary LIKE CONCAT('%', CAST(#{keyword} AS TEXT), '%'))</if>" +
            "</script>")
    long countUserPage(@Param("userId") Long userId,
                       @Param("noticeType") Integer noticeType,
                       @Param("noticeLevel") Integer noticeLevel,
                       @Param("isRead") Integer isRead,
                       @Param("keyword") String keyword);

    /** 取某用户所有可见已发布通知的ID集合（用于"标记全部已读"） */
    @Select("SELECT DISTINCT n.notice_id FROM sys_notice n WHERE n.send_status=1" +
            " AND (n.start_time IS NULL OR n.start_time<=NOW())" +
            " AND (n.end_time   IS NULL OR n.end_time  >=NOW())" +
            " AND (n.receiver_type=1" +
            "   OR (n.receiver_type=3 AND (CAST(#{userId} AS TEXT) = ANY(string_to_array(n.receiver_users, ','))))" +
            "   OR (n.receiver_type=2" +
            "      AND EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id=#{userId}" +
            "               AND (CAST(ur.role_id AS TEXT) = ANY(string_to_array(n.receiver_roles, ','))))))" +
            " ORDER BY n.notice_id")
    Set<Long> listNoticeIdsVisibleToUser(@Param("userId") Long userId);
}
