package com.pcba.erp.pdm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcba.erp.common.annotation.DataScope;
import com.pcba.erp.common.aspectj.DataScopeThreadLocal;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.entity.PdmMaterial;
import com.pcba.erp.pdm.entity.PdmMaterialSupplier;
import com.pcba.erp.pdm.mapper.PdmMaterialMapper;
import com.pcba.erp.pdm.mapper.PdmMaterialSupplierMapper;
import com.pcba.erp.pdm.service.IPdmMaterialSupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdmMaterialSupplierServiceImpl implements IPdmMaterialSupplierService {

    private final PdmMaterialSupplierMapper supplierMapper;
    private final PdmMaterialMapper materialMapper;

    @Override
    @DataScope(deptField = "s.dept_id", userField = "s.create_by")
    public List<PdmMaterialSupplier> listByMaterial(Long materialId, Long supplierId, Integer status) {
        String ds = DataScopeThreadLocal.getAndClear();
        return supplierMapper.listByMaterial(materialId, supplierId, status, ds);
    }

    @Override
    public PdmMaterialSupplier getById(Long id) {
        return supplierMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(PdmMaterialSupplier s) {
        validateAndFill(s, null);
        // is_default=1 则清其他默认
        if (isTrue(s.getIsDefault())) supplierMapper.clearOtherDefault(s.getMaterialId(), null);
        return supplierMapper.insert(s) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(PdmMaterialSupplier s) {
        if (s.getId() == null) throw new ServiceException("ID 必填");
        PdmMaterialSupplier old = supplierMapper.selectById(s.getId());
        if (old == null) throw new ServiceException("记录不存在");
        validateAndFill(s, old.getId());
        // is_default=1 时把其他条清 0
        if (isTrue(s.getIsDefault())) supplierMapper.clearOtherDefault(s.getMaterialId(), s.getId());
        s.setUpdateTime(LocalDateTime.now());
        return supplierMapper.updateById(s) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean replaceAll(Long materialId, List<PdmMaterialSupplier> list) {
        if (materialId == null) throw new ServiceException("物料ID 必填");
        if (materialMapper.selectById(materialId) == null) throw new ServiceException("物料不存在");
        // 删全部旧的
        supplierMapper.delete(new LambdaQueryWrapper<PdmMaterialSupplier>()
                .eq(PdmMaterialSupplier::getMaterialId, materialId));
        if (list == null || list.isEmpty()) return true;
        // 逐条校验 + insert (数量少≤20, 直接循环)
        boolean hasDefault = false;
        for (PdmMaterialSupplier s : list) {
            s.setId(null);
            s.setMaterialId(materialId);
            validateAndFill(s, null);
            if (isTrue(s.getIsDefault())) hasDefault = true;
            supplierMapper.insert(s);
        }
        return true;
    }

    @Override
    public boolean removeById(Long id) {
        return supplierMapper.deleteById(id) > 0;
    }

    /* ================= 私有 ================= */
    private void validateAndFill(PdmMaterialSupplier s, Long selfId) {
        // 1 物料存在
        if (s.getMaterialId() == null) throw new ServiceException("物料ID 必填");
        PdmMaterial m = materialMapper.selectById(s.getMaterialId());
        if (m == null) throw new ServiceException("物料不存在");
        // 2 供应商ID + 名称 必填 (供应商表阶段二才有, 只允许>0,不强制FK)
        if (s.getSupplierId() == null || s.getSupplierId() <= 0)
            throw new ServiceException("请选择供应商");
        if (s.getSupplierName() == null || s.getSupplierName().isBlank())
            throw new ServiceException("供应商名称必填");
        // 3 联合唯一 (material + supplier)
        if (supplierMapper.countDuplicateKey(s.getMaterialId(), s.getSupplierId(), selfId) > 0)
            throw new ServiceException("该物料已存在此供应商，请勿重复添加");
        // 4 priority 1~9
        if (s.getPriority() == null) s.setPriority(1);
        if (s.getPriority() < 1 || s.getPriority() > 9) s.setPriority(Math.min(9, Math.max(1, s.getPriority())));
        // 5 MOQ / MPQ 非负, 默认值
        if (s.getMoq() == null || s.getMoq().compareTo(BigDecimal.ZERO) < 0) s.setMoq(BigDecimal.ONE);
        if (s.getMpq() == null || s.getMpq().compareTo(BigDecimal.ZERO) < 0) s.setMpq(BigDecimal.ONE);
        if (s.getLeadTimeDays() == null || s.getLeadTimeDays() < 0) s.setLeadTimeDays(7);
        // 6 阶梯价: tier1 必填, tier2 非空则 qty>tier1_qty, tier3>tier2
        if (s.getTier1Qty() == null || s.getTier1Qty().compareTo(BigDecimal.ZERO) <= 0) s.setTier1Qty(BigDecimal.ONE);
        if (s.getTier1Price() == null || s.getTier1Price().compareTo(BigDecimal.ZERO) < 0)
            throw new ServiceException("阶梯1 单价必填且≥0");
        if (s.getTier2Qty() != null) {
            if (s.getTier2Price() == null) throw new ServiceException("阶梯2 单价必填");
            if (s.getTier2Qty().compareTo(s.getTier1Qty()) <= 0) throw new ServiceException("阶梯2起始数量必须大于阶梯1");
        }
        if (s.getTier3Qty() != null) {
            if (s.getTier3Price() == null) throw new ServiceException("阶梯3 单价必填");
            if (s.getTier2Qty() == null || s.getTier3Qty().compareTo(s.getTier2Qty()) <= 0)
                throw new ServiceException("阶梯3起始数量必须大于阶梯2");
        }
        // 7 币种/税率
        if (s.getCurrency() == null || s.getCurrency().isBlank()) s.setCurrency("CNY");
        if (s.getTaxRate() == null) s.setTaxRate(new BigDecimal("0.13"));
        if (s.getTaxRate().compareTo(BigDecimal.ZERO) < 0 || s.getTaxRate().compareTo(new BigDecimal("1")) > 0)
            throw new ServiceException("税率必须在 0~1 之间");
        // 8 有效期区间
        if (s.getEffectiveDate() != null && s.getExpiryDate() != null
                && s.getEffectiveDate().isAfter(s.getExpiryDate()))
            throw new ServiceException("生效日期必须早于失效日期");
        // 9 status
        if (s.getStatus() == null) s.setStatus(1);
        if (s.getIsDefault() == null) s.setIsDefault(0);
    }

    private static boolean isTrue(Integer v) { return v != null && v == 1; }
}
