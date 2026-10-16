package com.pcba.erp.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.system.entity.SysNotice;
import com.pcba.erp.system.entity.SysNoticeRead;
import com.pcba.erp.system.mapper.SysNoticeMapper;
import com.pcba.erp.system.mapper.SysNoticeReadMapper;
import com.pcba.erp.system.service.ISysNoticeService;
import com.pcba.erp.system.service.ISysNoticeReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysNoticeServiceImpl extends ServiceImpl<SysNoticeMapper, SysNotice> implements ISysNoticeService {

    private final ISysNoticeReadService readService;
    private final SysNoticeReadMapper readMapper;

    @Override
    public TableDataInfo<SysNotice> selectPage(int pageNum, int pageSize, Integer noticeType, Integer noticeLevel, Integer sendStatus, String title) {
        int offset = Math.max(0, (pageNum - 1) * pageSize);
        long total = baseMapper.count(noticeType, noticeLevel, sendStatus, title);
        List<SysNotice> rows = total == 0 ? Collections.emptyList()
                : baseMapper.selectPage(offset, pageSize, noticeType, noticeLevel, sendStatus, title);
        // 防止未读计算为负数（统计口径不一致时）
        for (SysNotice n : rows) {
            if (n.getUnreadCount() != null && n.getUnreadCount() < 0) n.setUnreadCount(0);
            if (n.getReadCount() != null && n.getReadCount() < 0) n.setReadCount(0);
        }
        return TableDataInfo.of(rows, total);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publish(Long noticeId, boolean publish, Long userId, String userName) {
        SysNotice n = getById(noticeId);
        if (n == null) throw new ServiceException("通知不存在");
        if (publish) {
            // 草稿或撤回都可以再发布
            if (n.getSendStatus() != null && n.getSendStatus() == 1) throw new ServiceException("已发布,不可重复发布");
            n.setSendStatus(1);
            n.setSendBy(userId);
            n.setSendByName(userName);
            n.setSendTime(LocalDateTime.now());
            if (n.getUpdateBy() == null) n.setUpdateBy(userId);
            n.setUpdateTime(LocalDateTime.now());
        } else {
            if (n.getSendStatus() == null || n.getSendStatus() != 1) throw new ServiceException("仅已发布状态可以撤回");
            n.setSendStatus(2);
            n.setUpdateBy(userId);
            n.setUpdateTime(LocalDateTime.now());
        }
        updateById(n);
        return 1;
    }

    @Override
    public TableDataInfo<SysNotice> selectUserList(Long userId, int pageNum, int pageSize, Integer noticeType, Integer noticeLevel, Integer isRead, String keyword) {
        int offset = Math.max(0, (pageNum - 1) * pageSize);
        long total = baseMapper.countUserPage(userId, noticeType, noticeLevel, isRead, keyword);
        List<SysNotice> rows = total == 0 ? Collections.emptyList()
                : baseMapper.selectUserPage(offset, pageSize, userId, noticeType, noticeLevel, isRead, keyword);
        return TableDataInfo.of(rows, total);
    }

    @Override
    public Map<String, Object> countUnread(Long userId) {
        // 按 notice_type 分别统计未读（is_read=0 或无记录）
        // 简单做法：分别 countUserPage 各 type（isRead=0），共 4 次查询，数据量很小，可接受
        Map<String, Object> m = new HashMap<>();
        long total = 0;
        for (int type = 1; type <= 3; type++) {
            long c = baseMapper.countUserPage(userId, type, null, 0, null);
            m.put("type" + type, (int) c);
            total += c;
        }
        // 审批待办（type=4）也统计一下
        long type4 = baseMapper.countUserPage(userId, 4, null, 0, null);
        m.put("type4", (int) type4);
        total += type4;
        m.put("total", (int) total);
        return m;
    }

    @Override
    public int markRead(Long noticeId, Long userId, String userName) {
        // 先确认用户是否在接收范围，避免非目标人插脏数据
        Set<Long> visible = baseMapper.listNoticeIdsVisibleToUser(userId);
        if (visible == null || !visible.contains(noticeId)) {
            throw new ServiceException("无权访问该通知");
        }
        return readMapper.upsertRead(noticeId, userId, userName, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(Long userId, String userName) {
        Set<Long> visible = baseMapper.listNoticeIdsVisibleToUser(userId);
        if (visible == null || visible.isEmpty()) return 0;
        // 取已存在的 (notice_id, user_id) 记录，增量 upsert
        LocalDateTime now = LocalDateTime.now();
        for (Long id : visible) {
            readMapper.upsertRead(id, userId, userName, now);
        }
        return visible.size();
    }

    @Override
    public List<SysNoticeRead> readList(Long noticeId) {
        // 按已读时间倒序
        List<SysNoticeRead> list = readService.lambdaQuery()
                .eq(SysNoticeRead::getNoticeId, noticeId)
                .eq(SysNoticeRead::getIsRead, 1)
                .orderByDesc(SysNoticeRead::getReadTime)
                .list();
        if (list == null) return Collections.emptyList();
        // 去重(相同用户可能重复，取最新1条)
        Map<Long, SysNoticeRead> uniq = new LinkedHashMap<>();
        for (SysNoticeRead r : list) uniq.putIfAbsent(r.getUserId(), r);
        return new ArrayList<>(uniq.values());
    }
}
