package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.PmmDefectCode;
import com.pcba.erp.pmm.mapper.PmmDefectCodeMapper;
import com.pcba.erp.pmm.service.IPmmDefectCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PmmDefectCodeServiceImpl implements IPmmDefectCodeService {

    private final PmmDefectCodeMapper mapper;

    @Override
    public IPage<PmmDefectCode> selectPage(int pageNum, int pageSize, String defectCode, String defectName,
                                           String defectCategory, Integer status) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), defectCode, defectName, defectCategory, status);
    }

    @Override
    public PmmDefectCode getDetail(Long id) {
        if (id == null) throw new ServiceException("不良代码ID不能为空");
        PmmDefectCode w = mapper.selectById(id);
        if (w == null) throw new ServiceException("不良代码不存在");
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PmmDefectCode entity) {
        if (entity.getDefectCode() != null) {
            if (mapper.countDuplicate(entity.getDefectCode(), null) > 0)
                throw new ServiceException("不良代码已存在");
        }
        if (entity.getStatus() == null) entity.setStatus(1);
        mapper.insert(entity);
        return entity.getCodeId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PmmDefectCode entity) {
        if (entity.getCodeId() == null) throw new ServiceException("不良代码ID不能为空");
        PmmDefectCode old = mapper.selectById(entity.getCodeId());
        if (old == null) throw new ServiceException("不良代码不存在");
        if (entity.getDefectCode() != null) {
            if (mapper.countDuplicate(entity.getDefectCode(), entity.getCodeId()) > 0)
                throw new ServiceException("不良代码已存在");
        }
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        if (id == null) throw new ServiceException("不良代码ID不能为空");
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer status) {
        if (id == null) throw new ServiceException("不良代码ID不能为空");
        if (status == null || (status != 0 && status != 1)) throw new ServiceException("状态值非法");
        PmmDefectCode w = mapper.selectById(id);
        if (w == null) throw new ServiceException("不良代码不存在");
        w.setStatus(status);
        return mapper.updateById(w) > 0;
    }

    @Override
    public List<PmmDefectCode> options() {
        return mapper.selectList(new LambdaQueryWrapper<PmmDefectCode>()
                .eq(PmmDefectCode::getStatus, 1)
                .orderByAsc(PmmDefectCode::getCodeId));
    }
}
