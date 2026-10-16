package com.pcba.erp.assembly.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.assembly.entity.AsmKitting;

import java.util.List;

/**
 * 配料单 Service
 */
public interface IAsmKittingService {

    IPage<AsmKitting> selectPage(int pageNum, int pageSize,
                                 String kittingNo, Long workOrderId, Integer status);

    AsmKitting getDetail(Long id);

    Long save(AsmKitting entity);

    boolean update(AsmKitting entity);

    boolean remove(Long id);

    List<AsmKitting> options();

    /** 生成配料单号 KT-yyyyMMdd-4位序号 */
    String nextKittingNo();

    /** 状态流转 */
    boolean startKitting(Long id);
    boolean finishKitting(Long id);
    boolean issueMaterial(Long id);
    boolean cancelKitting(Long id);
}
