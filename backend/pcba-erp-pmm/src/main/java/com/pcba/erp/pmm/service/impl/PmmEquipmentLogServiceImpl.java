package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.PmmEquipment;
import com.pcba.erp.pmm.entity.PmmEquipmentLog;
import com.pcba.erp.pmm.mapper.PmmEquipmentLogMapper;
import com.pcba.erp.pmm.mapper.PmmEquipmentMapper;
import com.pcba.erp.pmm.service.IPmmEquipmentLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PmmEquipmentLogServiceImpl implements IPmmEquipmentLogService {

    private final PmmEquipmentLogMapper logMapper;
    private final PmmEquipmentMapper    equipMapper;

    @Override
    public IPage<PmmEquipmentLog> selectPage(int pageNum, int pageSize, Long equipmentId, String equipmentNo,
                                             String eventType, String eventResult) {
        return logMapper.selectPage(new Page<>(pageNum, pageSize), equipmentId, equipmentNo, eventType, eventResult);
    }

    @Override
    public PmmEquipmentLog getDetail(Long logId) {
        if (logId == null) throw new ServiceException("日志ID不能为空");
        PmmEquipmentLog l = logMapper.selectById(logId);
        if (l == null) throw new ServiceException("日志不存在");
        return l;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PmmEquipmentLog entity) {
        if (entity.getEquipmentId() == null) throw new ServiceException("设备ID不能为空");
        PmmEquipment eq = equipMapper.selectById(entity.getEquipmentId());
        if (eq == null) throw new ServiceException("设备不存在");
        if (entity.getEquipmentNo() == null || entity.getEquipmentNo().isBlank())
            entity.setEquipmentNo(eq.getEquipmentNo());
        if (entity.getEventTime() == null) entity.setEventTime(LocalDateTime.now());
        if (entity.getCostAmount() == null) entity.setCostAmount(BigDecimal.ZERO);

        logMapper.insert(entity);

        // ====== 日志类型联动 ======
        // 1) STATUS_CHANGE 状态变更: 同步主表 status
        if ("STATUS_CHANGE".equalsIgnoreCase(entity.getEventType()) && entity.getNewStatus() != null) {
            eq.setStatus(entity.getNewStatus());
            equipMapper.updateById(eq);
        }
        // 2) MAINTAIN保养: 更新主表 lastMaintainDate → 自动重新算 nextMaintainDate
        else if ("MAINTAIN".equalsIgnoreCase(entity.getEventType()) && entity.getEventTime() != null) {
            LocalDate d = entity.getEventTime().toLocalDate();
            eq.setLastMaintainDate(d);
            PmmEquipmentServiceImpl.refreshNextDates(eq);
            equipMapper.updateById(eq);
        }
        // 3) CALIBRATION校准: 更新主表 lastCalDate → 自动重新算 nextCalDate
        else if ("CALIBRATION".equalsIgnoreCase(entity.getEventType()) && entity.getEventTime() != null) {
            LocalDate d = entity.getEventTime().toLocalDate();
            eq.setLastCalDate(d);
            PmmEquipmentServiceImpl.refreshNextDates(eq);
            equipMapper.updateById(eq);
        }
        // 4) REPAIR维修: 若日志开始时状态是维修中, 修改完成后(PASS)把状态改回在用
        else if ("REPAIR".equalsIgnoreCase(entity.getEventType()) && "PASS".equalsIgnoreCase(entity.getEventResult())) {
            if (eq.getStatus() != null && eq.getStatus() == 3) {
                eq.setStatus(1);
                equipMapper.updateById(eq);
            }
        }
        return entity.getLogId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long logId) {
        PmmEquipmentLog l = logMapper.selectById(logId);
        if (l == null) throw new ServiceException("日志不存在");
        return logMapper.deleteById(logId) > 0;
    }
}
