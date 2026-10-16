package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.CompanyParam;
import com.pcba.erp.pmm.mapper.CompanyParamMapper;
import com.pcba.erp.pmm.service.IPmmCompanyParamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统参数 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyParamServiceImpl implements IPmmCompanyParamService {

    private final CompanyParamMapper paramMapper;

    @Override
    public List<CompanyParam> listAll() {
        return paramMapper.selectAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(CompanyParam entity) {
        if (entity == null) throw new ServiceException("系统参数不能为空");
        if (entity.getParamKey() == null || entity.getParamKey().isBlank())
            throw new ServiceException("参数键不能为空");
        if (entity.getParamId() == null) {
            if (entity.getStatus() == null) entity.setStatus(0);
            paramMapper.insert(entity);
        } else {
            paramMapper.updateById(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        if (id == null) throw new ServiceException("系统参数ID不能为空");
        CompanyParam param = paramMapper.selectById(id);
        if (param == null) throw new ServiceException("系统参数不存在");
        paramMapper.deleteById(id);
    }

    @Override
    public CompanyParam getByKey(String key) {
        if (key == null || key.isBlank()) throw new ServiceException("参数键不能为空");
        return paramMapper.selectOne(
                new LambdaQueryWrapper<CompanyParam>().eq(CompanyParam::getParamKey, key));
    }
}
