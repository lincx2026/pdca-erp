package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.FasRecord;

import java.util.List;

public interface IPmmFasRecordService {
    IPage<FasRecord> selectPage(int pageNum, int pageSize, Long orderId, String recordType, String operator);
    FasRecord getById(Long id);
    List<FasRecord> listByOrder(Long orderId);
    Long create(FasRecord entity);
    void remove(Long id);
}
