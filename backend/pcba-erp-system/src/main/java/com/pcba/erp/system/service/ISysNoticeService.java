package com.pcba.erp.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.system.entity.SysNotice;
import com.pcba.erp.system.entity.SysNoticeRead;

import java.util.List;
import java.util.Map;

public interface ISysNoticeService extends IService<SysNotice> {
    /** 管理端分页查询 */
    TableDataInfo<SysNotice> selectPage(int pageNum, int pageSize, Integer noticeType, Integer noticeLevel, Integer sendStatus, String title);

    /** 发布 / 撤回 */
    int publish(Long noticeId, boolean publish, Long userId, String userName);

    /** 用户端（当前登录人可见）分页查询 */
    TableDataInfo<SysNotice> selectUserList(Long userId, int pageNum, int pageSize, Integer noticeType, Integer noticeLevel, Integer isRead, String keyword);

    /** 未读统计：total / type1 / type2 / type3 */
    Map<String, Object> countUnread(Long userId);

    /** 标记某条已读 */
    int markRead(Long noticeId, Long userId, String userName);

    /** 标记全部已读（当前用户所有可见通知） */
    int markAllRead(Long userId, String userName);

    /** 某通知已读人员列表（管理端查看） */
    List<SysNoticeRead> readList(Long noticeId);
}
