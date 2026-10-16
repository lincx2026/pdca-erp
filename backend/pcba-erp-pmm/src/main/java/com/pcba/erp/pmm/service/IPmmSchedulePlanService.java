package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.PmmSchedulePlan;

import java.time.LocalDate;
import java.util.List;

public interface IPmmSchedulePlanService {
    IPage<PmmSchedulePlan> selectPage(int pageNum, int pageSize, LocalDate scheduleDate, String shift,
                                      Long workOrderId, String workCenter, String machineCode);
    PmmSchedulePlan getDetail(Long id);
    Long save(PmmSchedulePlan entity);
    boolean update(PmmSchedulePlan entity);
    boolean remove(Long id);
    List<PmmSchedulePlan> listByDate(LocalDate date);
}
