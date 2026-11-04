package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.entity.PdmMaterial;
import com.pcba.erp.pdm.service.IPdmMaterialService;
import com.pcba.erp.scm.entity.ScmPurchaseRequisition;
import com.pcba.erp.scm.entity.ScmPurchaseRequisitionLine;
import com.pcba.erp.scm.entity.ScmSupplierPrice;
import com.pcba.erp.scm.mapper.ScmSupplierPriceMapper;
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
    private final ScmSupplierPriceMapper priceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> saveBatch(List<ScmPurchaseRequisitionLine> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > 100) throw new ServiceException("请选择1至100条物料");
        Long requisitionId = lines.get(0).getRequisitionId();
        if (requisitionId == null || lines.stream().anyMatch(line -> !requisitionId.equals(line.getRequisitionId())))
            throw new ServiceException("批量明细必须属于同一请购单");
        return lines.stream().map(this::save).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshEstimates(Long requisitionId) {
        requireEditable(requisitionId);
        List<ScmPurchaseRequisitionLine> lines = mapper.selectList(new LambdaQueryWrapper<ScmPurchaseRequisitionLine>()
                .eq(ScmPurchaseRequisitionLine::getRequisitionId, requisitionId));
        for (ScmPurchaseRequisitionLine line : lines) {
            applyEstimate(line);
            mapper.updateById(line);
        }
        recomputeHeader(requisitionId);
    }

    @Override
    public ScmPurchaseRequisitionLine estimate(Long materialId, Long supplierId, BigDecimal qty) {
        if (materialId == null || qty == null || qty.signum() <= 0) throw new ServiceException("请选择物料并填写大于零的数量");
        PdmMaterial material = materialService.getById(materialId);
        if (material == null) throw new ServiceException("物料不存在");
        var query = new LambdaQueryWrapper<ScmSupplierPrice>()
                .eq(ScmSupplierPrice::getMaterialId, materialId).eq(ScmSupplierPrice::getStatus, 2)
                .le(ScmSupplierPrice::getValidFrom, java.time.LocalDate.now())
                .ge(ScmSupplierPrice::getValidTo, java.time.LocalDate.now())
                .le(ScmSupplierPrice::getMinQty, qty);
        if (supplierId != null) query.eq(ScmSupplierPrice::getSupplierId, supplierId);
        else query.eq(ScmSupplierPrice::getIsDefault, 1);
        query.orderByDesc(ScmSupplierPrice::getMinQty).orderByDesc(ScmSupplierPrice::getValidFrom).orderByDesc(ScmSupplierPrice::getPriceId).last("LIMIT 1");
        ScmSupplierPrice price = priceMapper.selectOne(query);
        ScmPurchaseRequisitionLine result = new ScmPurchaseRequisitionLine();
        result.setSupplierId(supplierId);
        if (price != null && price.getUnitPrice() != null) {
            result.setSupplierId(price.getSupplierId()); result.setSupplierPriceId(price.getPriceId());
            result.setEstimatePrice(price.getUnitPrice()); result.setEstimateCurrency(price.getCurrency()); result.setEstimateSource("QUOTE");
        } else if (material.getReferencePrice() != null) {
            result.setEstimatePrice(material.getReferencePrice()); result.setEstimateCurrency("CNY"); result.setEstimateSource("REFERENCE");
        } else {
            result.setEstimatePrice(BigDecimal.ZERO); result.setEstimateCurrency("CNY"); result.setEstimateSource("MISSING");
        }
        result.setEstimateAmount(result.getEstimatePrice().multiply(qty));
        return result;
    }

    private void applyEstimate(ScmPurchaseRequisitionLine line) {
        ScmPurchaseRequisitionLine estimate = estimate(line.getMaterialId(), line.getSupplierId(), line.getQty());
        line.setSupplierId(estimate.getSupplierId()); line.setSupplierPriceId(estimate.getSupplierPriceId());
        line.setEstimatePrice(estimate.getEstimatePrice()); line.setEstimateAmount(estimate.getEstimateAmount());
        line.setEstimateSource(estimate.getEstimateSource()); line.setEstimateCurrency(estimate.getEstimateCurrency());
    }

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
        requireEditable(entity.getRequisitionId());
        if (entity.getMaterialId() == null || entity.getQty() == null || entity.getQty().signum() <= 0) throw new ServiceException("请选择物料并填写大于零的数量");
        entity.setStatus(0); entity.setConvertedPoId(null); entity.setConvertedPoLineId(null); entity.setConvertedQty(BigDecimal.ZERO);
        // 物料回填
        fillMaterial(entity);
        applyEstimate(entity);
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
        requireEditable(old.getRequisitionId());
        if (entity.getQty() != null && entity.getQty().signum() <= 0) throw new ServiceException("数量必须大于零");
        entity.setStatus(old.getStatus());
        entity.setConvertedPoId(old.getConvertedPoId()); entity.setConvertedPoLineId(old.getConvertedPoLineId()); entity.setConvertedQty(old.getConvertedQty());
        // 物料回填（支持按新传入 materialId 重新带出）
        fillMaterial(entity);
        // 编辑时保持主键/主单/行号一致
        entity.setRequisitionId(old.getRequisitionId());
        if (entity.getLineNo() == null) entity.setLineNo(old.getLineNo());
        if (entity.getMaterialId() == null) entity.setMaterialId(old.getMaterialId());
        if (entity.getQty() == null) entity.setQty(old.getQty());
        applyEstimate(entity);
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
        requireEditable(r.getRequisitionId());
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

    private void requireEditable(Long id) {
        ScmPurchaseRequisition header = requisitionMapper.selectForUpdate(id);
        if (header == null || (!Integer.valueOf(0).equals(header.getStatus()) && !Integer.valueOf(4).equals(header.getStatus())))
            throw new ServiceException("仅草稿或已驳回的请购单可以修改明细");
    }

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
            if (requisitionMapper.updateById(header) != 1) throw new ServiceException("重算请购单金额失败");
        }
    }
}
