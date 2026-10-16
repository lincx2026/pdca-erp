package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.PmmJigLog;

import java.time.LocalDateTime;

/**
 * 工装治具动作日志 Service
 */
public interface IPmmJigLogService {

    IPage<PmmJigLog> selectPage(int pageNum, int pageSize, Long jigId, String jigNo,
                                String eventType, String eventTitle, String eventResult,
                                String borrowerName, LocalDateTime startTime, LocalDateTime endTime);

    PmmJigLog getDetail(Long logId);

    /** 日志禁止修改, 只允许新增/删除 */
    Long save(PmmJigLog entity);

    boolean remove(Long logId);
}
