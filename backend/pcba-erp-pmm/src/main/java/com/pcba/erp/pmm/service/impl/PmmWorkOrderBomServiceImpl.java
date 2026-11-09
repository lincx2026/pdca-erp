package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.PmmWorkOrderBom;
import com.pcba.erp.pmm.mapper.PmmWorkOrderBomMapper;
import com.pcba.erp.pmm.service.IPmmWorkOrderBomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PmmWorkOrderBomServiceImpl implements IPmmWorkOrderBomService {

    private final PmmWorkOrderBomMapper mapper;
    private final JdbcTemplate jdbc;

    @Override
    public IPage<PmmWorkOrderBom> selectPage(int pageNum, int pageSize, Long workOrderId, String materialCode, String materialName) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), workOrderId, materialCode, materialName);
    }

    @Override
    public PmmWorkOrderBom getDetail(Long id) {
        if (id == null) throw new ServiceException("BOM行ID不能为空");
        PmmWorkOrderBom w = mapper.selectById(id);
        if (w == null) throw new ServiceException("BOM行不存在");
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PmmWorkOrderBom entity) {
        if (entity.getWorkOrderId() == null) throw new ServiceException("工单ID不能为空");
        List<BigDecimal> quantities = jdbc.queryForList("SELECT order_qty FROM pmm_work_order WHERE work_order_id=? FOR UPDATE", BigDecimal.class, entity.getWorkOrderId());
        if (quantities.isEmpty()) throw new ServiceException("工单不存在");
        if (entity.getMaterialId() == null) throw new ServiceException("请选择物料");
        if (entity.getQtyPerUnit() == null || entity.getQtyPerUnit().signum() <= 0) throw new ServiceException("单件用量必须大于零");
        if (entity.getScrapRate() != null && (entity.getScrapRate().signum() < 0 || entity.getScrapRate().compareTo(new BigDecimal("100")) > 0)) throw new ServiceException("损耗率必须在0到100之间");
        if (entity.getRequiredQty() != null && entity.getRequiredQty().signum() < 0) throw new ServiceException("需求数量不能为负数");
        if (entity.getLineNo() == null) entity.setLineNo(jdbc.queryForObject("SELECT COALESCE(MAX(line_no),0)+1 FROM pmm_work_order_bom WHERE work_order_id=?", Integer.class, entity.getWorkOrderId()));
        if (mapper.countDuplicateLine(entity.getWorkOrderId(), entity.getLineNo(), null) > 0)
            throw new ServiceException("该工单下已存在相同行号");
        if (entity.getRequiredQty() == null) entity.setRequiredQty(quantities.get(0).multiply(entity.getQtyPerUnit()).multiply(BigDecimal.ONE.add((entity.getScrapRate() == null ? BigDecimal.ZERO : entity.getScrapRate()).divide(new BigDecimal("100")))).setScale(4, java.math.RoundingMode.HALF_UP));
        entity.setIssuedQty(BigDecimal.ZERO);
        entity.setReturnedQty(BigDecimal.ZERO);
        if (entity.getScrapRate() == null) entity.setScrapRate(BigDecimal.ZERO);
        mapper.insert(entity);
        return entity.getBomLineId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PmmWorkOrderBom entity) {
        if (entity.getBomLineId() == null) throw new ServiceException("BOM行ID不能为空");
        PmmWorkOrderBom old = mapper.selectById(entity.getBomLineId());
        if (old == null) throw new ServiceException("BOM行不存在");
        if (entity.getQtyPerUnit() != null && entity.getQtyPerUnit().signum() <= 0) throw new ServiceException("单件用量必须大于零");
        if (entity.getScrapRate() != null && (entity.getScrapRate().signum() < 0 || entity.getScrapRate().compareTo(new BigDecimal("100")) > 0)) throw new ServiceException("损耗率必须在0到100之间");
        if (entity.getRequiredQty() != null && entity.getRequiredQty().signum() < 0) throw new ServiceException("需求数量不能为负数");
        entity.setWorkOrderId(old.getWorkOrderId());
        entity.setIssuedQty(old.getIssuedQty()); entity.setReturnedQty(old.getReturnedQty());
        if (entity.getWorkOrderId() != null && entity.getLineNo() != null) {
            if (mapper.countDuplicateLine(entity.getWorkOrderId(), entity.getLineNo(), entity.getBomLineId()) > 0)
                throw new ServiceException("该工单下已存在相同行号");
        }
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        if (id == null) throw new ServiceException("BOM行ID不能为空");
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<PmmWorkOrderBom> listByWorkOrder(Long workOrderId) {
        return mapper.selectList(new LambdaQueryWrapper<PmmWorkOrderBom>()
                .eq(workOrderId != null, PmmWorkOrderBom::getWorkOrderId, workOrderId)
                .orderByAsc(PmmWorkOrderBom::getLineNo));
    }
}
