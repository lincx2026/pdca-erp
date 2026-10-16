package com.pcba.erp.assembly.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.assembly.entity.AsmFeederLoad;

import java.util.List;

/**
 * Feeder 上料 Service
 */
public interface IAsmFeederLoadService {

    IPage<AsmFeederLoad> selectPage(int pageNum, int pageSize,
                                    String equipmentCode, String feederNo,
                                    Long materialId, Integer status);

    AsmFeederLoad getDetail(Long id);

    Long save(AsmFeederLoad entity);

    boolean update(AsmFeederLoad entity);

    boolean remove(Long id);

    List<AsmFeederLoad> options();

    /** 上料 */
    boolean loadFeeder(AsmFeederLoad entity);

    /** 下料 */
    boolean unloadFeeder(Long id);

    /** 报废 */
    boolean scrapFeeder(Long id);
}
