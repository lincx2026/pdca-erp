package com.pcba.erp.pdm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.aspectj.DataScopeThreadLocal;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.entity.PdmBom;
import com.pcba.erp.pdm.entity.PdmProcessRoute;
import com.pcba.erp.pdm.entity.PdmProcessStep;
import com.pcba.erp.pdm.mapper.PdmBomMapper;
import com.pcba.erp.pdm.mapper.PdmProcessRouteMapper;
import com.pcba.erp.pdm.mapper.PdmProcessStepMapper;
import com.pcba.erp.pdm.service.IPdmProcessRouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdmProcessRouteServiceImpl implements IPdmProcessRouteService {

    private final PdmProcessRouteMapper routeMapper;
    private final PdmProcessStepMapper stepMapper;
    private final PdmBomMapper bomMapper;

    /* ========== 分页查询 ========== */
    @Override
    public IPage<PdmProcessRoute> selectRoutePage(int pageNum, int pageSize,
                                                  String routeCode, String routeName,
                                                  Long productId, String productCode, Integer status) {
        String ds = DataScopeThreadLocal.getAndClear();
        List<PdmProcessRoute> all = routeMapper.selectRouteList(routeCode, routeName,
                productId, productCode, status, ds);
        fillSummary(all);
        long total = all.size();
        int from = Math.min((pageNum - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        Page<PdmProcessRoute> page = new Page<>(pageNum, pageSize);
        page.setRecords(all.subList(from, to));
        page.setTotal(total);
        return page;
    }

    /* ========== 详情 ========== */
    @Override
    public PdmProcessRoute getDetail(Long routeId) {
        PdmProcessRoute route = routeMapper.selectById(routeId);
        if (route == null) throw new ServiceException("工艺路线不存在: " + routeId);
        List<PdmProcessStep> steps = stepMapper.selectByRouteId(routeId);
        route.setSteps(steps);
        fillSummary(route);
        return route;
    }

    /* ========== 编码生成 ========== */
    @Override
    public String nextRouteCode() {
        String prefix = "PR";
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seq = ThreadLocalRandom.current().nextInt(1, 999);
        return String.format("%s-%s-%04d", prefix, date, seq);
    }

    /* ========== 新增 ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveWithSteps(PdmProcessRoute route) {
        // 校验编码唯一
        PdmProcessRoute dup = routeMapper.selectOne(new LambdaQueryWrapper<PdmProcessRoute>()
                .eq(PdmProcessRoute::getRouteCode, route.getRouteCode())
                .last("LIMIT 1"));
        if (dup != null) throw new ServiceException("工艺路线编码已存在: " + route.getRouteCode());

        // 绑定产品物料编码/名称 (自动从 productId 填充)
        if (route.getProductId() != null && (route.getProductCode() == null || route.getProductName() == null)) {
            List<PdmBom> boms = bomMapper.selectList(new LambdaQueryWrapper<PdmBom>()
                    .eq(PdmBom::getProductMaterialId, route.getProductId())
                    .last("LIMIT 1"));
            if (!boms.isEmpty()) {
                route.setProductCode(boms.get(0).getProductMaterialCode());
                route.setProductName(boms.get(0).getProductMaterialName());
            }
        }

        // 绑定 BOM 版本编码
        if (route.getBomVersionId() != null) {
            PdmBom b = bomMapper.selectById(route.getBomVersionId());
            if (b != null) route.setBomVersionCode(b.getBomCode());
        }

        // 同产品默认唯一性
        if (route.getIsDefault() != null && route.getIsDefault() == 1) {
            routeMapper.update(null, new LambdaUpdateWrapper<PdmProcessRoute>()
                    .eq(PdmProcessRoute::getProductId, route.getProductId())
                    .eq(PdmProcessRoute::getIsDefault, 1)
                    .set(PdmProcessRoute::getIsDefault, 0));
        }

        if (route.getStatus() == null) route.setStatus(1);
        if (route.getIsDefault() == null) route.setIsDefault(0);
        routeMapper.insert(route);

        saveSteps(route.getRouteId(), route.getSteps());
        return route.getRouteId();
    }

    /* ========== 修改 ========== */
    @Override
    public boolean update(PdmProcessRoute route) {
        PdmProcessRoute old = routeMapper.selectById(route.getRouteId());
        if (old == null) throw new ServiceException("工艺路线不存在");

        // 设为默认时, 同产品其他路线取消默认
        if (route.getIsDefault() != null && route.getIsDefault() == 1
                && (old.getIsDefault() == null || old.getIsDefault() != 1)) {
            routeMapper.update(null, new LambdaUpdateWrapper<PdmProcessRoute>()
                    .eq(PdmProcessRoute::getProductId, old.getProductId())
                    .eq(PdmProcessRoute::getIsDefault, 1)
                    .ne(PdmProcessRoute::getRouteId, route.getRouteId())
                    .set(PdmProcessRoute::getIsDefault, 0));
        }

        // 不允许把最后一条默认路线取消默认
        long otherDefaults = routeMapper.selectCount(new LambdaQueryWrapper<PdmProcessRoute>()
                .eq(PdmProcessRoute::getProductId, old.getProductId())
                .eq(PdmProcessRoute::getIsDefault, 1)
                .ne(PdmProcessRoute::getRouteId, route.getRouteId()));
        if (otherDefaults == 0 && (route.getIsDefault() == null || route.getIsDefault() == 0)) {
            throw new ServiceException("该产品至少需要1条默认工艺路线");
        }
        return routeMapper.updateById(route) > 0;
    }

    /* ========== 删除 ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long routeId) {
        PdmProcessRoute r = routeMapper.selectById(routeId);
        if (r == null) throw new ServiceException("工艺路线不存在");
        if (r.getIsDefault() != null && r.getIsDefault() == 1) {
            long others = routeMapper.selectCount(new LambdaQueryWrapper<PdmProcessRoute>()
                    .eq(PdmProcessRoute::getProductId, r.getProductId())
                    .ne(PdmProcessRoute::getRouteId, routeId));
            if (others == 0) throw new ServiceException("该产品仅1条工艺路线, 不允许删除");
        }
        stepMapper.deleteByRouteId(routeId);
        return routeMapper.deleteById(routeId) > 0;
    }

    /* ========== 全量覆盖步骤 ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean replaceSteps(Long routeId, List<PdmProcessStep> steps) {
        PdmProcessRoute r = routeMapper.selectById(routeId);
        if (r == null) throw new ServiceException("工艺路线不存在");
        stepMapper.deleteByRouteId(routeId);
        saveSteps(routeId, steps);
        return true;
    }

    /* ========== 设为默认 ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setDefault(Long routeId) {
        PdmProcessRoute r = routeMapper.selectById(routeId);
        if (r == null) throw new ServiceException("工艺路线不存在");
        if (r.getStatus() != 1) throw new ServiceException("仅启用状态可设为默认");
        routeMapper.update(null, new LambdaUpdateWrapper<PdmProcessRoute>()
                .eq(PdmProcessRoute::getProductId, r.getProductId())
                .eq(PdmProcessRoute::getIsDefault, 1)
                .set(PdmProcessRoute::getIsDefault, 0));
        r.setIsDefault(1);
        return routeMapper.updateById(r) > 0;
    }

    @Override
    public boolean disable(Long routeId) {
        PdmProcessRoute r = routeMapper.selectById(routeId);
        if (r == null) throw new ServiceException("工艺路线不存在");
        r.setStatus(0);
        return routeMapper.updateById(r) > 0;
    }

    @Override
    public boolean enable(Long routeId) {
        PdmProcessRoute r = routeMapper.selectById(routeId);
        if (r == null) throw new ServiceException("工艺路线不存在");
        r.setStatus(1);
        return routeMapper.updateById(r) > 0;
    }

    /* ========== 复制 ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long sourceRouteId, String newRouteName) {
        PdmProcessRoute src = routeMapper.selectById(sourceRouteId);
        if (src == null) throw new ServiceException("源工艺路线不存在");

        PdmProcessRoute neo = new PdmProcessRoute();
        neo.setRouteCode(nextRouteCode());
        neo.setRouteName(newRouteName != null && !newRouteName.isEmpty() ? newRouteName : src.getRouteName() + " 副本");
        neo.setProductId(src.getProductId());
        neo.setProductCode(src.getProductCode());
        neo.setProductName(src.getProductName());
        neo.setBomVersionId(src.getBomVersionId());
        neo.setBomVersionCode(src.getBomVersionCode());
        neo.setIsDefault(0);
        neo.setStatus(0);
        neo.setRemark(src.getRemark());
        routeMapper.insert(neo);

        List<PdmProcessStep> srcSteps = stepMapper.selectByRouteId(sourceRouteId);
        List<PdmProcessStep> newSteps = srcSteps.stream().map(s -> {
            PdmProcessStep ns = new PdmProcessStep();
            ns.setStepNo(s.getStepNo());
            ns.setStepCode(s.getStepCode());
            ns.setStepName(s.getStepName());
            ns.setWorkCenter(s.getWorkCenter());
            ns.setStandardCycleMin(s.getStandardCycleMin());
            ns.setStandardLaborQty(s.getStandardLaborQty());
            ns.setTargetYieldRate(s.getTargetYieldRate());
            ns.setIsRequiredInspection(s.getIsRequiredInspection());
            ns.setIsKeyProcess(s.getIsKeyProcess());
            ns.setSopFileUrl(s.getSopFileUrl());
            ns.setRemark(s.getRemark());
            return ns;
        }).collect(Collectors.toList());
        saveSteps(neo.getRouteId(), newSteps);
        return neo.getRouteId();
    }

    /* ========== 根据产品查默认 ========== */
    @Override
    public PdmProcessRoute getDefaultRoute(Long productId) {
        return routeMapper.selectOne(new LambdaQueryWrapper<PdmProcessRoute>()
                .eq(PdmProcessRoute::getProductId, productId)
                .eq(PdmProcessRoute::getIsDefault, 1)
                .eq(PdmProcessRoute::getStatus, 1)
                .last("LIMIT 1"));
    }

    /* ============ 私有 ============ */
    private void saveSteps(Long routeId, List<PdmProcessStep> steps) {
        if (steps == null || steps.isEmpty()) return;
        int seq = 10;
        for (int i = 0; i < steps.size(); i++) {
            PdmProcessStep s = steps.get(i);
            if (s.getStepNo() == null) s.setStepNo(seq);
            s.setRouteId(routeId);
            if (s.getStandardCycleMin() == null) s.setStandardCycleMin(BigDecimal.ZERO);
            if (s.getStandardLaborQty() == null) s.setStandardLaborQty(1);
            if (s.getTargetYieldRate() == null) s.setTargetYieldRate(new BigDecimal("0.990000"));
            if (s.getIsRequiredInspection() == null) s.setIsRequiredInspection(0);
            if (s.getIsKeyProcess() == null) s.setIsKeyProcess(0);
            stepMapper.insert(s);
            seq = s.getStepNo() + 10;
        }
    }

    private void fillSummary(PdmProcessRoute route) {
        if (route == null || route.getRouteId() == null) return;
        List<PdmProcessStep> steps = route.getSteps();
        if (steps == null) {
            steps = stepMapper.selectByRouteId(route.getRouteId());
            route.setSteps(steps);
        }
        if (steps.isEmpty()) {
            route.setTotalCycleMin(BigDecimal.ZERO);
            route.setTotalLaborQty(0);
            route.setOverallYield(new BigDecimal("1.000000"));
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal yield = BigDecimal.ONE;
        int labor = 0;
        for (PdmProcessStep s : steps) {
            if (s.getStandardCycleMin() != null) {
                total = total.add(s.getStandardCycleMin());
                labor += Optional.ofNullable(s.getStandardLaborQty()).orElse(1);
            }
            if (s.getTargetYieldRate() != null) {
                yield = yield.multiply(s.getTargetYieldRate());
            }
        }
        route.setTotalCycleMin(total.setScale(4, RoundingMode.HALF_UP));
        route.setTotalLaborQty(labor);
        route.setOverallYield(yield.setScale(6, RoundingMode.HALF_UP));
    }

    private void fillSummary(Collection<PdmProcessRoute> routes) {
        if (routes == null) return;
        for (PdmProcessRoute r : routes) fillSummary(r);
    }
}
