package com.pcba.erp.assembly.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.assembly.entity.AsmKittingLine;

import java.util.List;

/**
 * 配料明细行 Service
 */
public interface IAsmKittingLineService {

    List<AsmKittingLine> listByKittingId(Long kittingId);

    IPage<AsmKittingLine> selectPage(int pageNum, int pageSize,
                                     Long kittingId, Long materialId, Integer lineStatus);

    AsmKittingLine getDetail(Long id);

    Long save(AsmKittingLine entity);

    boolean update(AsmKittingLine entity);

    boolean remove(Long id);

    List<AsmKittingLine> options();
}
