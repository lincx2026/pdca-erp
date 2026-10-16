package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.PmmJig;
import com.pcba.erp.pmm.entity.PmmJigLog;
import com.pcba.erp.pmm.mapper.PmmJigLogMapper;
import com.pcba.erp.pmm.mapper.PmmJigMapper;
import com.pcba.erp.pmm.service.IPmmJigLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PmmJigLogServiceImpl implements IPmmJigLogService {

    private final PmmJigLogMapper logMapper;
    private final PmmJigMapper    jigMapper;

    @Override
    public IPage<PmmJigLog> selectPage(int pageNum, int pageSize, Long jigId, String jigNo,
                                       String eventType, String eventTitle, String eventResult,
                                       String borrowerName, LocalDateTime startTime, LocalDateTime endTime) {
        return logMapper.selectPage(new Page<>(pageNum, pageSize),
                jigId, jigNo, eventType, eventTitle, eventResult, borrowerName, startTime, endTime);
    }

    @Override
    public PmmJigLog getDetail(Long logId) {
        if (logId == null) throw new ServiceException("日志ID不能为空");
        PmmJigLog l = logMapper.selectById(logId);
        if (l == null) throw new ServiceException("日志不存在");
        return l;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PmmJigLog entity) {
        if (entity.getJigId() == null) throw new ServiceException("工装ID不能为空");
        PmmJig j = jigMapper.selectById(entity.getJigId());
        if (j == null) throw new ServiceException("工装不存在");
        if (entity.getJigNo() == null || entity.getJigNo().isBlank()) entity.setJigNo(j.getJigNo());
        if (entity.getEventTime() == null) entity.setEventTime(LocalDateTime.now());
        if (entity.getEventResult() == null) entity.setEventResult("PASS");
        if (entity.getUsedCountAdd() == null) entity.setUsedCountAdd(0);
        if (entity.getCostAmount() == null) entity.setCostAmount(BigDecimal.ZERO);

        logMapper.insert(entity);
        // 工装治具**业务动作推荐通过 JigService 的 borrow/returnBack/calibrate 等调用**,
        // 但如果直接调 log.save, 也尝试按 eventType 做联动 (复用 JigServiceImpl 联动分发逻辑)
        switch (entity.getEventType() == null ? "" : entity.getEventType().toUpperCase()) {
            case "BORROW"    -> doBorrow(j, entity);
            case "RETURN"    -> doReturn(j, entity);
            case "CALIBRATE" -> doCalibrate(j, entity);
            case "RESHARPEN" -> doResharpen(j, entity);
            case "REPAIR"    -> doRepair(j, entity);
            case "SCRAP"     -> doScrap(j, entity);
        }
        return entity.getLogId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long logId) {
        PmmJigLog l = logMapper.selectById(logId);
        if (l == null) throw new ServiceException("日志不存在");
        return logMapper.deleteById(logId) > 0;
    }

    // ================= 联动 helper (JigServiceImpl 直接复用) =================

    static void doBorrow(PmmJig j, PmmJigLog l) {
        if (j.getStatus() != null && (j.getStatus() == 5 || j.getStatus() == 6 || j.getStatus() == 4 || j.getStatus() == 3))
            throw new ServiceException("当前状态【" + statusName(j.getStatus()) + "】禁止领用");
        if (j.getQtyAvailable() == null || j.getQtyAvailable() < 1)
            throw new ServiceException("可用数量不足(可用=" + j.getQtyAvailable() + "套)");
        if (l.getBorrowerId() == null && (l.getBorrowerName() == null || l.getBorrowerName().isBlank()))
            throw new ServiceException("领用人不能为空");
        // 前端 Dialog: planReturnDate → 映射到 returnDate
        if (l.getReturnDate() == null && l.getPlanReturnDate() != null) {
            l.setReturnDate(l.getPlanReturnDate());
        }
        j.setQtyAvailable(j.getQtyAvailable() - 1);
        j.setQtyBorrowed((j.getQtyBorrowed() == null ? 0 : j.getQtyBorrowed()) + 1);
        // 记录整体借出的领用人(只在"首笔借出"时写入)
        if (l.getBorrowerName() != null) {
            j.setBorrowerId(l.getBorrowerId());
            j.setBorrowerName(l.getBorrowerName());
        }
        // 如果借出前 status=0(闲置) 或 1(在用) -> 改成借出(2)
        if (j.getStatus() == null || j.getStatus() == 0 || j.getStatus() == 1) j.setStatus(2);
    }

    static void doReturn(PmmJig j, PmmJigLog l) {
        if (j.getQtyBorrowed() == null || j.getQtyBorrowed() < 1)
            throw new ServiceException("该工装当前没有借出记录，无法归还");
        if (l.getUsedCountAdd() == null || l.getUsedCountAdd() < 0)
            throw new ServiceException("归还时请填写【本次使用次数】(>=0)");
        // 累加 used_count
        j.setUsedCount((j.getUsedCount() == null ? 0 : j.getUsedCount()) + l.getUsedCountAdd());
        // 数量回滚
        j.setQtyBorrowed(j.getQtyBorrowed() - 1);
        j.setQtyAvailable((j.getQtyAvailable() == null ? 0 : j.getQtyAvailable()) + 1);
        // 回填实际归还日期
        if (l.getRealReturnDate() == null) l.setRealReturnDate(l.getEventTime().toLocalDate());
        // 借出数量=0后, 清掉领用人记录, status 改为 0闲置
        if (j.getQtyBorrowed() <= 0) {
            j.setQtyBorrowed(0);
            j.setBorrowerId(null);
            j.setBorrowerName(null);
            // 如果之前状态=借出(2) 改回闲置(0); 其他状态如校准中/维修中不动
            if (j.getStatus() != null && j.getStatus() == 2) j.setStatus(0);
        }
        // 寿命 >= life_count * 100%  → 强制转 "修磨中(4)" 并禁止再次借出 下次
        // 寿命 >= 90% 只是预警不强制(仍可借)
        if (j.getLifeCount() != null && j.getLifeCount() > 0) {
            if (j.getUsedCount() >= j.getLifeCount()) {
                j.setStatus(4); // 修磨中
            }
        }
    }

    static void doCalibrate(PmmJig j, PmmJigLog l) {
        j.setLastCalDate(l.getEventTime().toLocalDate());
        if (j.getCalCycleDays() != null && j.getCalCycleDays() > 0) {
            j.setNextCalDate(j.getLastCalDate().plusDays(j.getCalCycleDays()));
        }
        if (j.getStatus() != null && j.getStatus() == 3) j.setStatus(0); // 校准完→闲置
    }

    static void doResharpen(PmmJig j, PmmJigLog l) {
        // used_count 清零, 寿命重新开始
        j.setUsedCount(0);
        if (j.getStatus() != null && j.getStatus() != 6) j.setStatus(0); // 修磨完→闲置
    }

    static void doRepair(PmmJig j, PmmJigLog l) {
        // 日志开始前状态一般改 维修中(5), 如果 REPAIR 结果 = PASS → 改回 0闲置
        if ("PASS".equalsIgnoreCase(l.getEventResult())) {
            j.setStatus(0);
        } else {
            j.setStatus(5);
        }
    }

    static void doScrap(PmmJig j, PmmJigLog l) {
        // 扣减 qty_current / qty_available
        if (j.getQtyCurrent() != null && j.getQtyCurrent() > 0) {
            j.setQtyCurrent(j.getQtyCurrent() - 1);
        }
        if (j.getQtyAvailable() != null && j.getQtyAvailable() > 0) {
            j.setQtyAvailable(j.getQtyAvailable() - 1);
        }
        // 所有套数都已报废 → status 变 6 报废
        if (j.getQtyCurrent() == null || j.getQtyCurrent() <= 0) {
            j.setQtyCurrent(0);
            j.setQtyAvailable(0);
            j.setQtyBorrowed(0);
            j.setStatus(6);
        }
    }

    static String statusName(Integer s) {
        if (s == null) return "";
        return switch (s) {
            case 0 -> "闲置";
            case 1 -> "在用";
            case 2 -> "借出";
            case 3 -> "校准中";
            case 4 -> "修磨中";
            case 5 -> "维修中";
            case 6 -> "报废";
            default -> String.valueOf(s);
        };
    }
}
