package com.pcba.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 通知公告 sys_notice */
@Data
@NoArgsConstructor
@TableName("sys_notice")
public class SysNotice implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long noticeId;

    /** 1=系统公告 2=业务通知 3=预警提醒 4=审批待办 */
    private Integer noticeType;

    /** 1=普通 2=重要 3=紧急 */
    private Integer noticeLevel;

    private String title;

    /** 正文内容（HTML/文本） */
    private String content;

    /** 摘要（列表/卡片展示用） */
    private String summary;

    /** 接收对象：1=全体 2=指定角色 3=指定用户 */
    private Integer receiverType;

    /** 接收角色ID（逗号分隔） */
    private String receiverRoles;

    /** 接收用户ID（逗号分隔） */
    private String receiverUsers;

    /** 关联业务类型 */
    private String relatedType;

    /** 关联业务ID */
    private String relatedId;

    /** 跳转路由 */
    private String actionRoute;

    /** 0否 1是 */
    private Integer isTop;

    /** 发送状态：0草稿 1已发布 2已撤回 */
    private Integer sendStatus;

    private Long sendBy;
    private String sendByName;
    private LocalDateTime sendTime;

    /** 生效范围（可空） */
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private Long createBy;
    private LocalDateTime createTime;
    private Long updateBy;
    private LocalDateTime updateTime;
    private String remark;

    // ====== 非表字段 ======
    /** 当前用户是否已读（1已读/0未读），用户端列表用 */
    @TableField(exist = false)
    private Integer readFlag;

    /** 已读人数，管理端用 */
    @TableField(exist = false)
    private Integer readCount;

    /** 未读人数，管理端用 */
    @TableField(exist = false)
    private Integer unreadCount;
}
