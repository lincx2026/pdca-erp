package com.pcba.erp.assembly.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.assembly.entity.AsmKittingPick;

import java.util.List;

/**
 * 拣货记录 Service
 */
public interface IAsmKittingPickService {

    IPage<AsmKittingPick> selectPage(int pageNum, int pageSize,
                                     Long kittingId, Long materialId, Integer pickStatus);

    AsmKittingPick getDetail(Long id);

    Long save(AsmKittingPick entity);

    boolean update(AsmKittingPick entity);

    boolean remove(Long id);

    List<AsmKittingPick> options();

    /** 生成拣货单号 PK-yyyyMMdd-4位序号 */
    String nextPickNo();
}
