package com.pcba.erp.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动通知事件 — 业务模块通过 ApplicationEventPublisher 发布，
 * 由 system 模块的 NoticeEventListener 异步落库为 sys_notice 记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeEvent {
    /** 1=系统公告 2=业务通知 3=预警提醒 4=审批待办 */
    @Builder.Default
    private int noticeType = 2;

    /** 1=普通 2=重要 3=紧急 */
    @Builder.Default
    private int noticeLevel = 1;

    private String title;
    private String content;
    private String summary;

    /** 1=全体 2=指定角色 3=指定用户 */
    @Builder.Default
    private int receiverType = 1;
    private String receiverRoles;
    private String receiverUsers;

    private String relatedType;
    private String relatedId;
    private String actionRoute;

    @Builder.Default
    private boolean top = false;
}
