package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.PmmWorkReport;

import java.util.List;

public interface IPmmWorkReportService {
    IPage<PmmWorkReport> selectPage(int pageNum, int pageSize, String reportNo, Long workOrderId, Long taskId,
                                    Long operatorId, String reportType, String shift);
    PmmWorkReport getDetail(Long id);
    Long save(PmmWorkReport entity);
    boolean update(PmmWorkReport entity);
    boolean remove(Long id);
    List<PmmWorkReport> listByWorkOrder(Long workOrderId);
}
