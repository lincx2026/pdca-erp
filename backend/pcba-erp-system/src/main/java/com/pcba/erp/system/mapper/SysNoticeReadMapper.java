package com.pcba.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.system.entity.SysNoticeRead;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface SysNoticeReadMapper extends BaseMapper<SysNoticeRead> {

    /** 幂等插入或更新已读状态 */
    @Insert("INSERT INTO sys_notice_read (notice_id, user_id, user_name, is_read, read_time) " +
            "VALUES (#{noticeId}, #{userId}, #{userName}, 1, #{readTime}) " +
            "ON CONFLICT (notice_id, user_id) DO UPDATE SET is_read=1, user_name=EXCLUDED.user_name, read_time=EXCLUDED.read_time")
    int upsertRead(@Param("noticeId") Long noticeId,
                   @Param("userId") Long userId,
                   @Param("userName") String userName,
                   @Param("readTime") LocalDateTime readTime);
}
