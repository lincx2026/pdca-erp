package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.PmmEquipmentLog;

public interface IPmmEquipmentLogService {

    IPage<PmmEquipmentLog> selectPage(int pageNum, int pageSize, Long equipmentId, String equipmentNo,
                                      String eventType, String eventResult);

    PmmEquipmentLog getDetail(Long logId);

    /** 新增事件日志: 若为状态变更事件, 会同步更新主表 status */
    Long save(PmmEquipmentLog entity);

    boolean remove(Long logId);
}
