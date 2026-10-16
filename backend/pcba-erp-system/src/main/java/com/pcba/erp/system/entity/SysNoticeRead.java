package com.pcba.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 通知已读记录 sys_notice_read */
@Data
@NoArgsConstructor
@TableName("sys_notice_read")
public class SysNoticeRead implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long readId;

    private Long noticeId;

    private Long userId;
    private String userName;

    /** 0未读 1已读 */
    private Integer isRead;

    private LocalDateTime readTime;
}
