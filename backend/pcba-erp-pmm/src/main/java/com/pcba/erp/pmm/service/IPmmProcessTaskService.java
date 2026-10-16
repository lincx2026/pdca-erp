package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.PmmProcessTask;

import java.util.List;

public interface IPmmProcessTaskService {
    IPage<PmmProcessTask> selectPage(int pageNum, int pageSize, Long workOrderId, String processName,
                                     String workCenter, Integer status, Long assignedUserId);
    PmmProcessTask getDetail(Long id);
    Long save(PmmProcessTask entity);
    boolean update(PmmProcessTask entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<PmmProcessTask> listByWorkOrder(Long workOrderId);
}
