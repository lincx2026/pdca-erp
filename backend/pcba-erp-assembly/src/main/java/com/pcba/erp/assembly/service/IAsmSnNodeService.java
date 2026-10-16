package com.pcba.erp.assembly.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.assembly.entity.AsmSnNode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SN 工序节点 Service
 */
public interface IAsmSnNodeService {

    IPage<AsmSnNode> selectPage(int pageNum, int pageSize,
                                Long snId, String nodeType, Long equipmentId,
                                LocalDateTime nodeTimeFrom, LocalDateTime nodeTimeTo);

    AsmSnNode getDetail(Long id);

    Long save(AsmSnNode entity);

    boolean update(AsmSnNode entity);

    boolean remove(Long id);

    List<AsmSnNode> options();

    /** 为指定 SN 的下一条节点分配 seqNo */
    int nextSeqNo(Long snId);
}
