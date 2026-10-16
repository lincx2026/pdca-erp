package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.entity.PdmMaterial;
import com.pcba.erp.pdm.service.IPdmMaterialService;
import com.pcba.erp.scm.entity.ScmPurchaseRequisition;
import com.pcba.erp.scm.entity.ScmPurchaseRequisitionLine;
import com.pcba.erp.scm.mapper.ScmPurchaseRequisitionLineMapper;
import com.pcba.erp.scm.mapper.ScmPurchaseRequisitionMapper;
import com.pcba.erp.scm.service.IScmPurchaseRequisitionLineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScmPurchaseRequisitionLineServiceImpl implements IScmPurchaseRequisitionLineService {

    private final ScmPurchaseRequisitionLineMapper mapper;
    private final IPdmMaterialService materialService;
    private final ScmPurchaseRequisitionMapper requisitionMapper;

    @Override
    public IPage<ScmPurchaseRequisitionLine> selectPage(int pageNum, int pageSize, Long requisitionId, String materialCode, String materialName) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), requisitionId, materialCode, materialName);
    }

    @Override
    public ScmPurchaseRequisitionLine getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        ScmPurchaseRequisitionLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScmPurchaseRequisitionLine entity) {
        // 物料回填
        fillMaterial(entity);
        // lineNo 按主单递增
        if (entity.getLineNo() == null || entity.getLineNo() <= 0) {
            Integer max = null;
            try {
                max = mapper.selectObjs(new LambdaQueryWrapper<ScmPurchaseRequisitionLine>()
                                .select(ScmPurchaseRequisitionLine::getLineNo)
                                .eq(ScmPurchaseRequisitionLine::getRequisitionId, entity.getRequisitionId())
                                .orderByDesc(ScmPurchaseRequisitionLine::getLineNo)
                                .last("LIMIT 1"))
                        .stream().findFirst().map(o -> ((Number) o).intValue()).orElse(0);
            } catch (Exception ignore) { max = 0; }
            entity.setLineNo((max == null ? 0 : max) + 10);
        }
        // 金额自动计算
        BigDecimal q = entity.getQty() == null ? BigDecimal.ZERO : entity.getQty();
        BigDecimal p = entity.getEstimatePrice() == null ? BigDecimal.ZERO : entity.getEstimatePrice();
        if ((entity.getEstimateAmount() == null || BigDecimal.ZERO.compareTo(entity.getEstimateAmount()) == 0)
                && (BigDecimal.ZERO.compareTo(q) != 0 || BigDecimal.ZERO.compareTo(p) != 0)) {
            entity.setEstimateAmount(q.multiply(p));
        }
        if (entity.getQty() == null) entity.setQty(BigDecimal.ZERO);
        if (entity.getEstimatePrice() == null) entity.setEstimatePrice(BigDecimal.ZERO);
        if (entity.getEstimateAmount() == null) entity.setEstimateAmount(BigDecimal.ZERO);

        mapper.insert(entity);
        // 主单合计重算
        recomputeHeader(entity.getRequisitionId());
        return entity.getLineId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ScmPurchaseRequisitionLine entity) {
        Long id = entity.getLineId();
        if (id == null) throw new ServiceException("ID不能为空");
        ScmPurchaseRequisitionLine old = mapper.selectById(id);
        if (old == null) throw new ServiceException("记录不存在");
        // 物料回填（支持按新传入 materialId 重新带出）
        fillMaterial(entity);
        // 编辑时保持主键/主单/行号一致
        entity.setRequisitionId(old.getRequisitionId());
        if (entity.getLineNo() == null) entity.setLineNo(old.getLineNo());
        // 金额自动重算（当显式传了 qty 或 price）
        BigDecimal q = entity.getQty() != null ? entity.getQty() : old.getQty();
        BigDecimal p = entity.getEstimatePrice() != null ? entity.getEstimatePrice() : old.getEstimatePrice();
        if (entity.getEstimateAmount() == null && q != null && p != null) {
            entity.setEstimateAmount(q.multiply(p));
        } else if (entity.getEstimateAmount() != null && BigDecimal.ZERO.compareTo(entity.getEstimateAmount()) == 0
                && entity.getQty() != null && entity.getEstimatePrice() != null) {
            entity.setEstimateAmount(entity.getQty().multiply(entity.getEstimatePrice()));
        }
        boolean ok = mapper.updateById(entity) > 0;
        if (ok) recomputeHeader(old.getRequisitionId());
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        ScmPurchaseRequisitionLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        boolean ok = mapper.deleteById(id) > 0;
        if (ok) recomputeHeader(r.getRequisitionId());
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer status) {
        if (id == null) throw new ServiceException("ID不能为空");
        if (status == null) throw new ServiceException("状态值非法");
        ScmPurchaseRequisitionLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        try {
            Field s = ScmPurchaseRequisitionLine.class.getDeclaredField("status");
            s.setAccessible(true);
            s.set(r, status);
        } catch (Exception ignore) { throw new ServiceException("状态字段缺失"); }
        return mapper.updateById(r) > 0;
    }

    @Override
    public List<ScmPurchaseRequisitionLine> options() {
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScmPurchaseRequisitionLine>()
                .orderByDesc("line_id"));
    }

    /* ========== 私有工具：物料回填 + 主单合计重算 ========== */

    /** 按 materialId 从 PDM 带出编码/名称/规格/单位（前端传入的优先） */
    private void fillMaterial(ScmPurchaseRequisitionLine entity) {
        if (entity == null || entity.getMaterialId() == null) return;
        PdmMaterial m;
        try { m = materialService.getById(entity.getMaterialId()); } catch (Exception ignore) { m = null; }
        if (m == null) return;
        if ((entity.getMaterialCode() == null || entity.getMaterialCode().isBlank()) && m.getMaterialCode() != null) {
            entity.setMaterialCode(m.getMaterialCode());
        }
        if ((entity.getMaterialName() == null || entity.getMaterialName().isBlank()) && m.getMaterialName() != null) {
            entity.setMaterialName(m.getMaterialName());
        }
        if ((entity.getSpecification() == null || entity.getSpecification().isBlank()) && m.getSpecification() != null) {
            entity.setSpecification(m.getSpecification());
        }
        if ((entity.getUnit() == null || entity.getUnit().isBlank()) && m.getUnit() != null) {
            entity.setUnit(m.getUnit());
        }
    }

    /** 重算主单 totalAmount = SUM(estimateAmount) — 直接走 RequisitionMapper，避免 Service 循环依赖 */
    private void recomputeHeader(Long requisitionId) {
        if (requisitionId == null) return;
        ScmPurchaseRequisition header = requisitionMapper.selectById(requisitionId);
        if (header == null) return;
        List<ScmPurchaseRequisitionLine> rows = mapper.selectList(
                new LambdaQueryWrapper<ScmPurchaseRequisitionLine>().eq(ScmPurchaseRequisitionLine::getRequisitionId, requisitionId));
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ScmPurchaseRequisitionLine line : rows) {
            BigDecimal a = line.getEstimateAmount();
            totalAmount = totalAmount.add(a == null ? BigDecimal.ZERO : a);
        }
        if (header.getTotalAmount() == null || header.getTotalAmount().compareTo(totalAmount) != 0) {
            header.setTotalAmount(totalAmount);
            try { requisitionMapper.updateById(header); } catch (Exception ignore) { log.warn("重算采购申请主单失败: {}", requisitionId); }
        }
    }
}
