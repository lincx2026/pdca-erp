package com.pcba.erp.assembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.assembly.entity.AsmSnNode;
import com.pcba.erp.assembly.mapper.AsmSnNodeMapper;
import com.pcba.erp.assembly.service.IAsmSnNodeService;
import com.pcba.erp.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SN 工序节点 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsmSnNodeServiceImpl implements IAsmSnNodeService {

    private final AsmSnNodeMapper nodeMapper;

    @Override
    public IPage<AsmSnNode> selectPage(int pageNum, int pageSize,
                                       Long snId, String nodeType, Long equipmentId,
                                       LocalDateTime nodeTimeFrom, LocalDateTime nodeTimeTo) {
        Page<AsmSnNode> page = new Page<>(pageNum, pageSize);
        return nodeMapper.selectPage(page, snId, nodeType, equipmentId, nodeTimeFrom, nodeTimeTo);
    }

    @Override
    public AsmSnNode getDetail(Long id) {
        AsmSnNode n = nodeMapper.selectById(id);
        if (n == null) throw new ServiceException("SN节点不存在");
        return n;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(AsmSnNode entity) {
        if (entity.getNodeTime() == null) entity.setNodeTime(LocalDateTime.now());
        if (entity.getSeqNo() == null) entity.setSeqNo(nextSeqNo(entity.getSnId()));
        if (nodeMapper.insert(entity) <= 0) throw new ServiceException("新增SN节点失败");
        return entity.getNodeId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(AsmSnNode entity) {
        if (entity.getNodeId() == null) throw new ServiceException("节点ID不能为空");
        AsmSnNode old = nodeMapper.selectById(entity.getNodeId());
        if (old == null) throw new ServiceException("SN节点不存在");
        return nodeMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        AsmSnNode n = nodeMapper.selectById(id);
        if (n == null) throw new ServiceException("SN节点不存在");
        return nodeMapper.deleteById(id) > 0;
    }

    @Override
    public List<AsmSnNode> options() {
        return nodeMapper.selectList(
                new LambdaQueryWrapper<AsmSnNode>()
                        .orderByDesc(AsmSnNode::getNodeId)
                        .last("LIMIT 200"));
    }

    @Override
    public int nextSeqNo(Long snId) {
        if (snId == null) return 1;
        AsmSnNode last = nodeMapper.selectOne(
                new LambdaQueryWrapper<AsmSnNode>()
                        .eq(AsmSnNode::getSnId, snId)
                        .orderByDesc(AsmSnNode::getSeqNo)
                        .last("LIMIT 1"));
        return last == null ? 1 : (last.getSeqNo() == null ? 1 : last.getSeqNo() + 1);
    }
}
