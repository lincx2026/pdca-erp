package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.FasRecord;
import com.pcba.erp.pmm.mapper.FasRecordMapper;
import com.pcba.erp.pmm.service.IPmmFasRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FasRecordServiceImpl implements IPmmFasRecordService {

    private final FasRecordMapper mapper;

    @Override
    public IPage<FasRecord> selectPage(int pageNum, int pageSize, Long orderId, String recordType, String operator) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), orderId, recordType, operator);
    }

    @Override
    public FasRecord getById(Long id) {
        if (id == null) throw new ServiceException("记录ID不能为空");
        FasRecord record = mapper.selectById(id);
        if (record == null) throw new ServiceException("记录不存在");
        return record;
    }

    @Override
    public List<FasRecord> listByOrder(Long orderId) {
        if (orderId == null) throw new ServiceException("工单ID不能为空");
        return mapper.listByOrder(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(FasRecord entity) {
        if (entity.getRecordType() == null || entity.getRecordType().isEmpty()) {
            throw new ServiceException("记录类型不能为空");
        }
        if (entity.getEventTime() == null) entity.setEventTime(java.time.LocalDateTime.now());
        mapper.insert(entity);
        return entity.getRecordId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        if (id == null) throw new ServiceException("记录ID不能为空");
        FasRecord record = mapper.selectById(id);
        if (record == null) throw new ServiceException("记录不存在");
        mapper.deleteById(id);
    }
}
