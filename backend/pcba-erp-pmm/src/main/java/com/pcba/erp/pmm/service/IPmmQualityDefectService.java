package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.PmmQualityDefect;

import java.util.List;

public interface IPmmQualityDefectService {
    IPage<PmmQualityDefect> selectPage(int pageNum, int pageSize, Long workOrderId, String defectCode,
                                       String defectLevel, String checkType, String disposition,
                                       String processName, Long inspectorId);
    PmmQualityDefect getDetail(Long id);
    Long save(PmmQualityDefect entity);
    boolean update(PmmQualityDefect entity);
    boolean remove(Long id);
    List<PmmQualityDefect> listByWorkOrder(Long workOrderId);
}
