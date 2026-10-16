package com.pcba.erp.assembly.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.assembly.entity.AsmSnRelation;

import java.util.List;

/**
 * SN 父子关系 Service
 */
public interface IAsmSnRelationService {

    IPage<AsmSnRelation> selectPage(int pageNum, int pageSize,
                                    Long parentSnId, Long childSnId);

    AsmSnRelation getDetail(Long id);

    Long save(AsmSnRelation entity);

    boolean update(AsmSnRelation entity);

    boolean remove(Long id);

    List<AsmSnRelation> options();
}
