package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.FasBom;
import com.pcba.erp.pmm.mapper.FasBomMapper;
import com.pcba.erp.pmm.service.IPmmFasBomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FasBomServiceImpl implements IPmmFasBomService {

    private final FasBomMapper mapper;

    @Override
    public IPage<FasBom> selectPage(int pageNum, int pageSize, Long productId, String componentName, String componentType) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), productId, componentName, componentType);
    }

    @Override
    public FasBom getById(Long id) {
        if (id == null) throw new ServiceException("BOM ID不能为空");
        FasBom bom = mapper.selectById(id);
        if (bom == null) throw new ServiceException("BOM不存在");
        return bom;
    }

    @Override
    public List<FasBom> listByProduct(Long productId) {
        if (productId == null) throw new ServiceException("成品ID不能为空");
        return mapper.listByProduct(productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(FasBom entity) {
        if (entity.getProductId() == null) throw new ServiceException("成品ID不能为空");
        if (entity.getComponentId() == null) throw new ServiceException("子件ID不能为空");
        if (entity.getNeedFeedCheck() == null) entity.setNeedFeedCheck(0);
        if (entity.getFeedSequence() == null) entity.setFeedSequence(0);
        mapper.insert(entity);
        return entity.getBomId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FasBom entity) {
        if (entity.getBomId() == null) throw new ServiceException("BOM ID不能为空");
        FasBom old = mapper.selectById(entity.getBomId());
        if (old == null) throw new ServiceException("BOM不存在");
        mapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        if (id == null) throw new ServiceException("BOM ID不能为空");
        FasBom bom = mapper.selectById(id);
        if (bom == null) throw new ServiceException("BOM不存在");
        mapper.deleteById(id);
    }
}
