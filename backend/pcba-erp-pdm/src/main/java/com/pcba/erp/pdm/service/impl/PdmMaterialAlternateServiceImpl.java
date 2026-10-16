package com.pcba.erp.pdm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcba.erp.common.annotation.DataScope;
import com.pcba.erp.common.aspectj.DataScopeThreadLocal;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.entity.PdmMaterial;
import com.pcba.erp.pdm.entity.PdmMaterialAlternate;
import com.pcba.erp.pdm.mapper.PdmMaterialAlternateMapper;
import com.pcba.erp.pdm.mapper.PdmMaterialMapper;
import com.pcba.erp.pdm.service.IPdmMaterialAlternateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdmMaterialAlternateServiceImpl implements IPdmMaterialAlternateService {

    private final PdmMaterialAlternateMapper altMapper;
    private final PdmMaterialMapper materialMapper;

    @Override
    @DataScope(deptField = "a.dept_id", userField = "a.create_by")
    public List<PdmMaterialAlternate> listAlternates(Long mainMaterialId, Long altMaterialId,
                                                     String groupId, Integer status) {
        String ds = DataScopeThreadLocal.getAndClear();
        return altMapper.listAlternates(mainMaterialId, altMaterialId, groupId, status, ds);
    }

    @Override
    public PdmMaterialAlternate getById(Long id) { return altMapper.selectById(id); }

    @Override
    public String nextGroupId() {
        String base = LocalDateTime.now().format(DateTimeFormatter.ofPattern("'ALT-'yyMMddHHmmss"));
        int rnd = ThreadLocalRandom.current().nextInt(10, 100);
        return base + rnd;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(PdmMaterialAlternate a) {
        validateCommon(a, null);
        if (a.getStatus() == null) a.setStatus(0);
        return altMapper.insert(a) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(PdmMaterialAlternate a) {
        if (a.getId() == null) throw new ServiceException("ID 必填");
        PdmMaterialAlternate old = altMapper.selectById(a.getId());
        if (old == null) throw new ServiceException("替代关系不存在");
        // 生效状态 2 不允许直接修改除 status
        if (Integer.valueOf(2).equals(old.getStatus())
                && !Integer.valueOf(3).equals(a.getStatus())) {
            // 仅允许改为停用(状态=3); 其他字段不动(我们仍然把其他字段保留old, 简化体验)
            if (a.getStatus() == null || a.getStatus() != 3)
                throw new ServiceException("生效中的替代关系不能修改字段内容, 只能「停用」或先驳回/作废");
        }
        validateCommon(a, a.getId());
        a.setUpdateTime(LocalDateTime.now());
        return altMapper.updateById(a) > 0;
    }

    @Override
    public boolean removeById(Long id) {
        PdmMaterialAlternate old = altMapper.selectById(id);
        if (old != null && Integer.valueOf(2).equals(old.getStatus()))
            throw new ServiceException("生效中的替代关系请先停用再删除");
        return altMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean replaceAll(Long mainMaterialId, List<PdmMaterialAlternate> list) {
        if (mainMaterialId == null) throw new ServiceException("主料ID 必填");
        if (materialMapper.selectById(mainMaterialId) == null) throw new ServiceException("主料不存在");
        // 删主料下所有替代
        altMapper.delete(new LambdaQueryWrapper<PdmMaterialAlternate>()
                .eq(PdmMaterialAlternate::getMainMaterialId, mainMaterialId));
        if (list == null || list.isEmpty()) return true;
        // 组号：优先用首行 groupId，若都空则系统生成
        String groupId = list.stream()
                .filter(a -> a.getGroupId() != null && !a.getGroupId().isBlank())
                .map(PdmMaterialAlternate::getGroupId)
                .findFirst().orElseGet(this::nextGroupId);
        int i = 0;
        for (PdmMaterialAlternate a : list) {
            a.setId(null);
            a.setMainMaterialId(mainMaterialId);
            a.setGroupId(groupId);
            if (a.getPriority() == null) a.setPriority(++i);
            validateCommon(a, null);
            if (a.getStatus() == null) a.setStatus(0);
            altMapper.insert(a);
        }
        return true;
    }

    /* ============ 状态机 ============ */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submitAudit(Long id) {
        PdmMaterialAlternate a = requireStatus(id, 0, 4);
        a.setStatus(1);
        a.setRejectReason(null);
        return altMapper.updateById(a) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean auditPass(Long id, String auditBy) {
        PdmMaterialAlternate a = requireStatus(id, 1);
        // 生效日不填=今天
        a.setStatus(2);
        if (a.getEffectiveDate() == null) a.setEffectiveDate(LocalDate.now());
        a.setAuditBy(auditBy);
        a.setAuditTime(LocalDateTime.now());
        return altMapper.updateById(a) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean auditReject(Long id, String auditBy, String rejectReason) {
        PdmMaterialAlternate a = requireStatus(id, 1);
        a.setStatus(4);
        a.setAuditBy(auditBy);
        a.setAuditTime(LocalDateTime.now());
        a.setRejectReason(rejectReason == null ? "驳回原因未填写" : rejectReason);
        return altMapper.updateById(a) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disable(Long id) {
        PdmMaterialAlternate a = requireStatus(id, 2);
        a.setStatus(3);
        return altMapper.updateById(a) > 0;
    }

    /* ================= 私有 ================= */
    private void validateCommon(PdmMaterialAlternate a, Long selfId) {
        // 主料/替代料 非空+存在+互异
        if (a.getMainMaterialId() == null) throw new ServiceException("主料 必填");
        if (a.getAltMaterialId() == null) throw new ServiceException("替代料 必填");
        if (a.getMainMaterialId().equals(a.getAltMaterialId()))
            throw new ServiceException("主料与替代料不能是同一个物料");
        PdmMaterial main = materialMapper.selectById(a.getMainMaterialId());
        if (main == null) throw new ServiceException("主料不存在");
        PdmMaterial alt = materialMapper.selectById(a.getAltMaterialId());
        if (alt == null) throw new ServiceException("替代料不存在");
        // groupId 空时系统生成(批量 replaceAll 外层会赋值，单条保存兜底)
        if (a.getGroupId() == null || a.getGroupId().isBlank()) a.setGroupId(nextGroupId());
        // 主料+替代料 组内不重复（即使不同 groupId 也不允许同主同替）
        if (altMapper.countSameAlt(a.getMainMaterialId(), a.getAltMaterialId(), selfId) > 0)
            throw new ServiceException("替代关系已存在：主料「" + main.getMaterialCode()
                    + "」已有替代料「" + alt.getMaterialCode() + "」");
        // alt_type
        if (a.getAltType() == null) a.setAltType(1);
        if (a.getAltType() < 1 || a.getAltType() > 3) a.setAltType(1);
        // priority
        if (a.getPriority() == null) a.setPriority(1);
        if (a.getPriority() < 1) a.setPriority(1);
        // 替代比例：正数
        if (a.getSubRate() == null || a.getSubRate().compareTo(BigDecimal.ZERO) <= 0) a.setSubRate(BigDecimal.ONE);
        // 有效期
        if (a.getEffectiveDate() != null && a.getExpiryDate() != null
                && a.getEffectiveDate().isAfter(a.getExpiryDate()))
            throw new ServiceException("生效日期必须早于失效日期");
    }

    private PdmMaterialAlternate requireStatus(Long id, Integer... allowed) {
        if (id == null) throw new ServiceException("ID 必填");
        PdmMaterialAlternate a = altMapper.selectById(id);
        if (a == null) throw new ServiceException("替代关系不存在");
        if (allowed != null) {
            boolean ok = false;
            for (Integer x : allowed) if (x.equals(a.getStatus())) { ok = true; break; }
            if (!ok) throw new ServiceException("当前状态不允许此操作");
        }
        return a;
    }
}
