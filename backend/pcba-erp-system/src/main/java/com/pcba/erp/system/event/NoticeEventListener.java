package com.pcba.erp.system.event;

import com.alibaba.fastjson2.JSONObject;
import com.pcba.erp.common.event.NoticeEvent;
import com.pcba.erp.common.websocket.WebSocketSessionManager;
import com.pcba.erp.system.entity.SysNotice;
import com.pcba.erp.system.mapper.SysNoticeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 监听 NoticeEvent，异步写入 sys_notice（自动发布，sendStatus=1），
 * 并通过 WebSocket 实时推送到在线用户。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeEventListener {

    private final SysNoticeMapper noticeMapper;
    private final WebSocketSessionManager sessionManager;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Async
    @EventListener
    public void onNoticeEvent(NoticeEvent e) {
        try {
            SysNotice n = new SysNotice();
            n.setNoticeType(e.getNoticeType());
            n.setNoticeLevel(e.getNoticeLevel());
            n.setTitle(e.getTitle());
            n.setContent(e.getContent());
            n.setSummary(e.getSummary());
            n.setReceiverType(e.getReceiverType());
            n.setReceiverRoles(e.getReceiverRoles());
            n.setReceiverUsers(e.getReceiverUsers());
            n.setRelatedType(e.getRelatedType());
            n.setRelatedId(e.getRelatedId());
            n.setActionRoute(e.getActionRoute());
            n.setIsTop(e.isTop() ? 1 : 0);
            n.setSendStatus(1);       // 自动发布
            n.setSendBy(0L);          // 系统自动
            n.setSendByName("系统自动");
            n.setSendTime(LocalDateTime.now());
            noticeMapper.insert(n);
            log.info("[NoticeEventListener] 自动通知已生成: {}", e.getTitle());

            // WebSocket 实时推送
            pushWebSocket(n);

        } catch (Exception ex) {
            log.error("[NoticeEventListener] 自动通知保存失败: {}", e.getTitle(), ex);
        }
    }

    /**
     * 构造 JSON 消息并推送。
     * receiverType: 1=全体 → 广播; 3=指定用户 → 定向; 2=指定角色 → 广播（简化）
     */
    private void pushWebSocket(SysNotice n) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "NOTICE_NEW");
            msg.put("noticeId", n.getNoticeId());
            msg.put("noticeType", n.getNoticeType());
            msg.put("noticeLevel", n.getNoticeLevel());
            msg.put("title", n.getTitle());
            msg.put("summary", n.getSummary());
            msg.put("sendByName", n.getSendByName());
            msg.put("sendTime", n.getSendTime() != null ? n.getSendTime().format(DT_FMT) : null);
            msg.put("actionRoute", n.getActionRoute());
            String json = msg.toJSONString();

            if (n.getReceiverType() == 3 && n.getReceiverUsers() != null && !n.getReceiverUsers().isEmpty()) {
                // 指定用户 → 定向推送
                for (String uid : n.getReceiverUsers().split(",")) {
                    String id = uid.trim();
                    if (!id.isEmpty()) {
                        sessionManager.sendToUser(id, json);
                    }
                }
            } else {
                // 全体或指定角色 → 广播
                sessionManager.sendToAll(json);
            }
        } catch (Exception e) {
            log.warn("[NoticeEventListener] WebSocket 推送失败: {}", e.getMessage());
        }
    }
}
