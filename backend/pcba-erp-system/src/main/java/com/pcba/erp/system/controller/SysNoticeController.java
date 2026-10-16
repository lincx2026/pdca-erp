package com.pcba.erp.system.controller;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.system.entity.SysNotice;
import com.pcba.erp.system.entity.SysNoticeRead;
import com.pcba.erp.system.service.ISysNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "通知管理")
@RestController
@RequiredArgsConstructor
public class SysNoticeController {

    private final ISysNoticeService noticeService;

    // =====================================================================
    // 管理端：/system/notice/**  （带权限）
    // =====================================================================

    /** 管理端分页列表 */
    @Operation(summary = "通知-管理端分页")
    @PreAuthorize("@ps.hasPermi('system:notice:list') or @ps.hasPermi('system:notice:query')")
    @GetMapping("/system/notice/list")
    public TableDataInfo<SysNotice> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer noticeType,
            @RequestParam(required = false) Integer noticeLevel,
            @RequestParam(required = false) Integer sendStatus,
            @RequestParam(required = false) String title) {
        return noticeService.selectPage(pageNum, pageSize, noticeType, noticeLevel, sendStatus, title);
    }

    /** 管理端详情 */
    @Operation(summary = "通知-管理端详情")
    @PreAuthorize("@ps.hasPermi('system:notice:query')")
    @GetMapping("/system/notice/{id}")
    public R<SysNotice> get(@PathVariable Long id) {
        return R.ok(noticeService.getById(id));
    }

    /** 新增（默认 draft） */
    @Operation(summary = "通知-新增")
    @PreAuthorize("@ps.hasPermi('system:notice:add')")
    @OperLog(title = "通知管理", businessType = 1)
    @PostMapping("/system/notice")
    public R<Integer> add(@RequestBody SysNotice body) {
        Long uid = SecurityUtils.getUserIdSafe();
        body.setNoticeId(null);
        if (body.getSendStatus() == null) body.setSendStatus(0); // 默认草稿
        body.setCreateBy(uid);
        body.setCreateTime(LocalDateTime.now());
        body.setUpdateBy(uid);
        body.setUpdateTime(LocalDateTime.now());
        // 直接发布情况
        if (body.getSendStatus() != null && body.getSendStatus() == 1) {
            body.setSendBy(uid);
            body.setSendByName(SecurityUtils.getNickName());
            body.setSendTime(LocalDateTime.now());
        }
        noticeService.save(body);
        return R.ok(body.getNoticeId() == null ? 0 : 1);
    }

    /** 修改 */
    @Operation(summary = "通知-修改")
    @PreAuthorize("@ps.hasPermi('system:notice:edit')")
    @OperLog(title = "通知管理", businessType = 2)
    @PutMapping("/system/notice")
    public R<Integer> edit(@RequestBody SysNotice body) {
        if (body.getNoticeId() == null) return R.fail("ID不能为空");
        Long uid = SecurityUtils.getUserIdSafe();
        body.setUpdateBy(uid);
        body.setUpdateTime(LocalDateTime.now());
        // 不允许在 edit 里改发布人、发送状态；发布/撤回走独立接口
        body.setSendBy(null);
        body.setSendByName(null);
        body.setSendTime(null);
        noticeService.updateById(body);
        return R.ok(1);
    }

    /** 删除 */
    @Operation(summary = "通知-删除")
    @PreAuthorize("@ps.hasPermi('system:notice:remove')")
    @OperLog(title = "通知管理", businessType = 3)
    @DeleteMapping("/system/notice/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        SysNotice n = noticeService.getById(id);
        if (n == null) throw new ServiceException("通知不存在");
        noticeService.removeById(id);
        return R.ok(1);
    }

    /** 发布 / 撤回 */
    @Operation(summary = "通知-发布或撤回")
    @PreAuthorize("@ps.hasPermi('system:notice:send')")
    @OperLog(title = "通知管理", businessType = 4)
    @PostMapping("/system/notice/{id}/publish")
    public R<Integer> publish(@PathVariable Long id, @RequestParam boolean publish) {
        Long uid = SecurityUtils.getUserIdSafe();
        String name = SecurityUtils.getNickName();
        return R.ok(noticeService.publish(id, publish, uid, name));
    }

    /** 某通知的已读人员列表（管理端查看） */
    @Operation(summary = "通知-已读记录")
    @PreAuthorize("@ps.hasPermi('system:notice:read')")
    @GetMapping("/system/notice/{id}/read")
    public R<List<SysNoticeRead>> readList(@PathVariable Long id) {
        return R.ok(noticeService.readList(id));
    }

    // =====================================================================
    // 用户端：/notice/user/**  （只要登录即可访问，无权限码）
    // =====================================================================

    /** 当前登录用户：我的消息列表（分页） */
    @Operation(summary = "通知-用户端分页")
    @GetMapping("/notice/user/list")
    public TableDataInfo<SysNotice> userList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer noticeType,
            @RequestParam(required = false) Integer noticeLevel,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(required = false) String keyword) {
        Long uid = SecurityUtils.getUserId();
        return noticeService.selectUserList(uid, pageNum, pageSize, noticeType, noticeLevel, isRead, keyword);
    }

    /** 当前登录用户：未读统计（total / type1..type4） */
    @Operation(summary = "通知-未读数量统计")
    @GetMapping("/notice/user/count")
    public R<Map<String, Object>> userCount() {
        Long uid = SecurityUtils.getUserId();
        return R.ok(noticeService.countUnread(uid));
    }

    /** 标记某条已读 */
    @Operation(summary = "通知-标记单条已读")
    @PostMapping("/notice/user/{id}/read")
    public R<Integer> markRead(@PathVariable Long id) {
        Long uid = SecurityUtils.getUserId();
        String name = SecurityUtils.getNickName();
        return R.ok(noticeService.markRead(id, uid, name));
    }

    /** 标记全部已读 */
    @Operation(summary = "通知-标记全部已读")
    @PostMapping("/notice/user/read-all")
    public R<Integer> markAllRead() {
        Long uid = SecurityUtils.getUserId();
        String name = SecurityUtils.getNickName();
        return R.ok(noticeService.markAllRead(uid, name));
    }

    /** 我的消息详情（含自动标记已读） */
    @Operation(summary = "通知-用户端详情(自动已读)")
    @GetMapping("/notice/user/{id}")
    public R<SysNotice> userGet(@PathVariable Long id) {
        Long uid = SecurityUtils.getUserId();
        String name = SecurityUtils.getNickName();
        SysNotice n = noticeService.getById(id);
        if (n == null) throw new ServiceException("通知不存在");
        // 先校验 + 标记已读
        noticeService.markRead(id, uid, name);
        return R.ok(n);
    }
}
