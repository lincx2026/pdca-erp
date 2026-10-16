package com.pcba.erp.assembly.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.assembly.entity.AsmSn;

import java.util.List;

/**
 * SN 主表 Service
 */
public interface IAsmSnService {

    IPage<AsmSn> selectPage(int pageNum, int pageSize,
                            String snCode, String snType, Integer snLevel,
                            Integer currentStatus, Long workOrderId, Long materialId);

    AsmSn getDetail(Long id);

    Long save(AsmSn entity);

    boolean update(AsmSn entity);

    boolean remove(Long id);

    List<AsmSn> options();

    /** 更新 SN 当前状态 */
    boolean updateStatus(Long snId, Integer currentStatus, String currentProcess);

    /** 生成 SN 编码 SN-yyyyMMdd-4位序号 */
    String nextSnCode();
}
