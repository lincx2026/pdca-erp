package com.pcba.erp.sal.service.impl;

import com.pcba.erp.sal.entity.SalKittingLine;
import com.pcba.erp.sal.mapper.SalKittingLineMapper;
import com.pcba.erp.sal.service.ISalKittingLineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SalKittingLineServiceImpl implements ISalKittingLineService {

    private final SalKittingLineMapper lineMapper;

    @Override
    public List<SalKittingLine> listByKittingId(Long kittingId) {
        return lineMapper.selectByKittingId(kittingId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SalKittingLine entity) {
        lineMapper.insert(entity);
        return entity.getKittingLineId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeByKittingId(Long kittingId) {
        return lineMapper.deleteByKittingId(kittingId) >= 0;
    }
}
