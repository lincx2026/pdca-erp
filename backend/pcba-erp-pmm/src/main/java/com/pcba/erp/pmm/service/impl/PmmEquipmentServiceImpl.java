package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.event.NoticeEvent;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.PmmEquipment;
import com.pcba.erp.pmm.mapper.PmmEquipmentMapper;
import com.pcba.erp.pmm.service.IPmmEquipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PmmEquipmentServiceImpl implements IPmmEquipmentService {

    private final PmmEquipmentMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public IPage<PmmEquipment> selectPage(int pageNum, int pageSize, String equipmentNo, String equipmentName,
                                           String equipmentType, Integer status, Integer equipLevel,
                                           String workshop, String workCenter) {
        return mapper.selectPage(new Page<>(pageNum, pageSize),
                equipmentNo, equipmentName, equipmentType, status, equipLevel, workshop, workCenter);
    }

    @Override
    public PmmEquipment getDetail(Long id) {
        if (id == null) throw new ServiceException("设备ID不能为空");
        PmmEquipment e = mapper.selectById(id);
        if (e == null) throw new ServiceException("设备不存在");
        return e;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PmmEquipment entity) {
        if (mapper.countDuplicateNo(entity.getEquipmentNo(), null) > 0)
            throw new ServiceException("设备编码【" + entity.getEquipmentNo() + "】已存在");
        if (entity.getStatus() == null) entity.setStatus(0);
        if (entity.getEquipLevel() == null) entity.setEquipLevel(2);
        if (entity.getCapacityPerHour() == null) entity.setCapacityPerHour(BigDecimal.ZERO);
        if (entity.getCapacityUnit() == null) entity.setCapacityUnit("PCS");
        if (entity.getMaintainCycleDays() == null) entity.setMaintainCycleDays(90);
        if (entity.getCalibrationCycleDays() == null) entity.setCalibrationCycleDays(0);
        // 自动计算下次保养/校准日期
        refreshNextDates(entity);
        mapper.insert(entity);
        // 自动通知：新设备建档
        eventPublisher.publishEvent(NoticeEvent.builder()
                .noticeType(2).noticeLevel(1)
                .title("新设备 " + entity.getEquipmentNo() + " 已建档")
                .summary(entity.getEquipmentName() + " (" + entity.getEquipmentNo() + ")")
                .content("<p>新设备 <b>" + entity.getEquipmentNo() + " " + entity.getEquipmentName() + "</b> 已完成建档登记。</p>")
                .relatedType("EQUIPMENT")
                .relatedId(String.valueOf(entity.getEquipmentId()))
                .actionRoute("/pm/equipment")
                .build());
        return entity.getEquipmentId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PmmEquipment entity) {
        if (entity.getEquipmentId() == null) throw new ServiceException("设备ID不能为空");
        PmmEquipment old = mapper.selectById(entity.getEquipmentId());
        if (old == null) throw new ServiceException("设备不存在");
        if (entity.getEquipmentNo() != null) {
            if (mapper.countDuplicateNo(entity.getEquipmentNo(), entity.getEquipmentId()) > 0)
                throw new ServiceException("设备编码已存在");
        }
        refreshNextDates(entity);
        boolean ok = mapper.updateById(entity) > 0;
        if (ok && entity.getStatus() != null && old.getStatus() != null && !entity.getStatus().equals(old.getStatus())) {
            String[] stNames = {"闲置", "在用", "保养中", "维修中", "报废"};
            String oldSt = old.getStatus() >= 0 && old.getStatus() < stNames.length ? stNames[old.getStatus()] : String.valueOf(old.getStatus());
            String newSt = entity.getStatus() >= 0 && entity.getStatus() < stNames.length ? stNames[entity.getStatus()] : String.valueOf(entity.getStatus());
            int level = (entity.getStatus() == 3 || entity.getStatus() == 4) ? 3 : 2; // 维修/报废 → 紧急
            eventPublisher.publishEvent(NoticeEvent.builder()
                    .noticeType(3).noticeLevel(level)
                    .title("设备 " + old.getEquipmentNo() + " 状态变更：" + oldSt + " → " + newSt)
                    .summary(old.getEquipmentName() + " (" + old.getEquipmentNo() + ")")
                    .content("<p>设备 <b>" + old.getEquipmentNo() + " " + old.getEquipmentName() + "</b> 状态由「" + oldSt + "」变更为「" + newSt + "」。</p>")
                    .relatedType("EQUIPMENT")
                    .relatedId(String.valueOf(entity.getEquipmentId()))
                    .actionRoute("/pm/equipment")
                    .build());
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        PmmEquipment e = mapper.selectById(id);
        if (e == null) throw new ServiceException("设备不存在");
        if (e.getStatus() != null && e.getStatus() == 1)
            throw new ServiceException("在用设备禁止直接删除, 请先改为闲置或报废");
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<PmmEquipment> options() {
        return mapper.selectList(new LambdaQueryWrapper<PmmEquipment>()
                .ne(PmmEquipment::getStatus, 4)
                .orderByAsc(PmmEquipment::getEquipmentNo));
    }

    /** 根据 lastDate + cycleDays 自动刷新下次保养/校准日期 */
    static void refreshNextDates(PmmEquipment e) {
        if (e.getLastMaintainDate() != null && e.getMaintainCycleDays() != null && e.getMaintainCycleDays() > 0) {
            e.setNextMaintainDate(e.getLastMaintainDate().plusDays(e.getMaintainCycleDays()));
        } else if (e.getInstallDate() != null && e.getMaintainCycleDays() != null && e.getMaintainCycleDays() > 0
                && e.getNextMaintainDate() == null) {
            e.setNextMaintainDate(e.getInstallDate().plusDays(e.getMaintainCycleDays()));
        }
        if (e.getLastCalDate() != null && e.getCalibrationCycleDays() != null && e.getCalibrationCycleDays() > 0) {
            e.setNextCalDate(e.getLastCalDate().plusDays(e.getCalibrationCycleDays()));
        } else if (e.getInstallDate() != null && e.getCalibrationCycleDays() != null && e.getCalibrationCycleDays() > 0
                && e.getNextCalDate() == null) {
            e.setNextCalDate(e.getInstallDate().plusDays(e.getCalibrationCycleDays()));
        }
    }
}
