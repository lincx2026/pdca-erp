package com.pcba.erp.sal.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.sal.entity.SalForecast;
import com.pcba.erp.sal.mapper.SalForecastMapper;
import com.pcba.erp.sal.service.ISalForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * 销售预测/滚动计划 ServiceImpl
 * 状态机: 0草稿 → 1生效 → 2已归档
 * 算法: MA-8移动平均(过去8周实际量÷8), 历史不足跳过避免噪音
 * 滚动: 每周一凌晨1:05 @Scheduled 自动生成新周版本+归档旧版本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalForecastServiceImpl implements ISalForecastService {

    private final SalForecastMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    private static final int MA_PERIODS = 8;      // MA-8
    private static final int RECENT_WEEKS = 4;    // 过去4周有订单才生成
    private static final int DEFAULT_PERIODS = 12; // 默认生成未来12周期

    /* ============ 分页/CRUD ============ */
    @Override
    public IPage<SalForecast> selectPage(int pageNum, int pageSize, String versionNo, String versionType,
                                          Long customerId, Long materialId, Integer status,
                                          String customerName, String materialName) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), versionNo, versionType,
                customerId, materialId, status, customerName, materialName);
    }

    @Override
    public SalForecast getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        SalForecast r = mapper.selectById(id);
        if (r == null) throw new ServiceException("预测记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SalForecast e) {
        if (e.getVersionNo() == null || e.getVersionNo().isBlank()) throw new ServiceException("版本号不能为空");
        if (e.getVersionType() == null || e.getVersionType().isBlank()) throw new ServiceException("版本类型不能为空");
        if (e.getPeriodStart() == null || e.getPeriodEnd() == null) throw new ServiceException("周期不能为空");
        if (e.getForecastQty() == null) e.setForecastQty(BigDecimal.ZERO);
        if (e.getActualQty() == null) e.setActualQty(BigDecimal.ZERO);
        if (e.getActualAmt() == null) e.setActualAmt(BigDecimal.ZERO);
        if (e.getHitRate() == null) e.setHitRate(BigDecimal.ZERO);
        if (e.getForecastAmt() == null) e.setForecastAmt(BigDecimal.ZERO);
        if (e.getMethod() == null || e.getMethod().isBlank()) e.setMethod("MANUAL");
        if (e.getStatus() == null) e.setStatus(0);
        mapper.insert(e);
        return e.getForecastId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SalForecast e) {
        if (e.getForecastId() == null) throw new ServiceException("ID不能为空");
        SalForecast old = mapper.selectById(e.getForecastId());
        if (old == null) throw new ServiceException("记录不存在");
        if (old.getStatus() != null && old.getStatus() != 0)
            throw new ServiceException("仅草稿状态可修改");
        // 禁止通过 update 改状态/审批字段(由专门方法处理)
        e.setStatus(old.getStatus());
        e.setAuditBy(old.getAuditBy());
        e.setAuditTime(old.getAuditTime());
        return mapper.updateById(e) > 0;
    }

    @Override
    public boolean remove(Long id) {
        SalForecast r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        if (r.getStatus() != null && r.getStatus() != 0)
            throw new ServiceException("仅草稿状态可删除");
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<Map<String, Object>> listVersions() {
        return mapper.listVersions();
    }

    /* ============ 滚动生成核心 ============ */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generateVersion(String type, int periodsAhead, String operator) {
        if (type == null || (!type.equals("W") && !type.equals("M")))
            throw new ServiceException("版本类型仅支持 W(周) / M(月)");
        int periods = periodsAhead > 0 ? periodsAhead : DEFAULT_PERIODS;
        LocalDate today = LocalDate.now();

        // 1. 计算版本号 + 周期起点
        String versionNo;
        LocalDate baseStart;
        if (type.equals("W")) {
            baseStart = today.with(DayOfWeek.MONDAY);
            int weekNum = today.get(WeekFields.ISO.weekOfYear());
            versionNo = String.format("%d-W%02d", today.getYear(), weekNum);
        } else {
            baseStart = today.withDayOfMonth(1);
            versionNo = String.format("%d-M%02d", today.getYear(), today.getMonthValue());
        }

        // 2. 旧生效版本回写actual → 1→2 归档
        String lastVer = mapper.selectLastActiveVersion(type);
        if (lastVer != null) {
            rollbackActual(lastVer);
            int archived = jdbcTemplate.update(
                    "UPDATE sal_forecast SET status=2, update_time=NOW() " +
                            "WHERE version_no=? AND status=1", lastVer);
            log.info("[generateVersion] 旧版本 {} 归档 {} 行", lastVer, archived);
        }

        // 3. 幂等: 同版本号已存在则跳过(避免定时任务重复生成)
        Integer exists = mapper.selectVersionStatus(versionNo);
        if (exists != null) {
            log.warn("[generateVersion] 版本 {} 已存在(status={}), 跳过", versionNo, exists);
            return 0;
        }

        // 4. 找过去4周有订单的客户+物料组合(预测维度)
        LocalDate since = type.equals("W") ? today.minusWeeks(RECENT_WEEKS) : today.minusMonths(RECENT_WEEKS);
        List<Map<String, Object>> dimList = mapper.listRecentCustomerMaterials(since);
        if (dimList.isEmpty()) {
            log.warn("[generateVersion] 过去{}周无订单数据,跳过生成", RECENT_WEEKS);
            return 0;
        }

        // 5. MA-8 算法: 对每个维度查过去8周实际量 ÷ 8 = 周平均预测量
        LocalDate maFrom = type.equals("W") ? today.minusWeeks(MA_PERIODS) : today.minusMonths(MA_PERIODS);
        LocalDate maTo = today.minusDays(1);
        int totalRows = 0;
        for (Map<String, Object> dim : dimList) {
            Long cid = toLong(dim.get("customerId"));
            Long mid = toLong(dim.get("materialId"));
            String custName = toStr(dim.get("customerName"));
            String matCode = toStr(dim.get("materialCode"));
            String matName = toStr(dim.get("materialName"));
            String unit = toStr(dim.get("unit"));

            BigDecimal totalQty = mapper.sumOrderQty(cid, mid, maFrom, maTo);
            if (totalQty == null || totalQty.signum() <= 0) continue; // 历史不足跳过避免噪音

            BigDecimal avgQty = totalQty.divide(BigDecimal.valueOf(MA_PERIODS), 4, RoundingMode.HALF_UP);
            BigDecimal price = mapper.selectCustomerPrice(cid, mid);
            if (price == null) price = BigDecimal.ZERO;
            BigDecimal amt = avgQty.multiply(price).setScale(2, RoundingMode.HALF_UP);

            // 6. 生成未来 N 个周期
            for (int i = 0; i < periods; i++) {
                LocalDate pStart, pEnd;
                if (type.equals("W")) {
                    pStart = baseStart.plusWeeks(i);
                    pEnd = pStart.plusDays(6);
                } else {
                    pStart = baseStart.plusMonths(i);
                    pEnd = pStart.withDayOfMonth(pStart.lengthOfMonth());
                }
                SalForecast f = new SalForecast();
                f.setVersionNo(versionNo);
                f.setVersionType(type);
                f.setCustomerId(cid);
                f.setCustomerName(custName);
                f.setMaterialId(mid);
                f.setMaterialCode(matCode);
                f.setMaterialName(matName);
                f.setUnit(unit);
                f.setPeriodStart(pStart);
                f.setPeriodEnd(pEnd);
                f.setForecastQty(avgQty);
                f.setForecastAmt(amt);
                f.setActualQty(BigDecimal.ZERO);
                f.setActualAmt(BigDecimal.ZERO);
                f.setHitRate(BigDecimal.ZERO);
                f.setMethod("MA");
                f.setStatus(0);
                mapper.insert(f);
                totalRows++;
            }
        }
        log.info("[generateVersion] 版本 {} 生成 {} 维度 × {} 周期 = {} 行, operator={}",
                versionNo, dimList.size(), periods, totalRows, operator);
        return totalRows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rollbackActual(String versionNo) {
        if (versionNo == null || versionNo.isBlank()) return 0;
        List<SalForecast> rows = mapper.listByVersion(versionNo);
        LocalDate today = LocalDate.now();
        int updated = 0;
        for (SalForecast f : rows) {
            // 仅回写已过去周期(period_end < today)
            if (f.getPeriodEnd() == null || !f.getPeriodEnd().isBefore(today)) continue;
            BigDecimal qty = mapper.sumOrderQty(f.getCustomerId(), f.getMaterialId(),
                    f.getPeriodStart(), f.getPeriodEnd());
            if (qty == null) qty = BigDecimal.ZERO;
            BigDecimal price = mapper.selectCustomerPrice(f.getCustomerId(), f.getMaterialId());
            BigDecimal amt = (price == null) ? BigDecimal.ZERO : qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
            updated += mapper.updateActual(f.getForecastId(), qty, amt);
        }
        log.info("[rollbackActual] 版本 {} 回写 {} 行实际量", versionNo, updated);
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean auditVersion(String versionNo, String operator) {
        if (versionNo == null || versionNo.isBlank()) throw new ServiceException("版本号不能为空");
        Integer cur = mapper.selectVersionStatus(versionNo);
        if (cur == null) throw new ServiceException("版本不存在");
        if (cur != 0) throw new ServiceException("仅草稿状态可审核");
        LambdaUpdateWrapper<SalForecast> uw = new LambdaUpdateWrapper<>();
        uw.eq(SalForecast::getVersionNo, versionNo)
                .eq(SalForecast::getStatus, 0)
                .set(SalForecast::getStatus, 1)
                .set(SalForecast::getAuditBy, operator == null ? "" : operator)
                .set(SalForecast::getAuditTime, LocalDateTime.now());
        return mapper.update(null, uw) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean archiveVersion(String versionNo) {
        if (versionNo == null || versionNo.isBlank()) throw new ServiceException("版本号不能为空");
        Integer cur = mapper.selectVersionStatus(versionNo);
        if (cur == null) throw new ServiceException("版本不存在");
        if (cur != 1) throw new ServiceException("仅生效状态可归档");
        LambdaUpdateWrapper<SalForecast> uw = new LambdaUpdateWrapper<>();
        uw.eq(SalForecast::getVersionNo, versionNo)
                .eq(SalForecast::getStatus, 1)
                .set(SalForecast::getStatus, 2);
        return mapper.update(null, uw) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean adjustQty(Long forecastId, BigDecimal newQty, String operator) {
        if (forecastId == null) throw new ServiceException("ID不能为空");
        if (newQty == null || newQty.signum() < 0) throw new ServiceException("预测量必须≥0");
        SalForecast f = mapper.selectById(forecastId);
        if (f == null) throw new ServiceException("预测记录不存在");
        if (f.getStatus() != null && f.getStatus() != 0)
            throw new ServiceException("仅草稿状态可调整");
        BigDecimal price = mapper.selectCustomerPrice(f.getCustomerId(), f.getMaterialId());
        BigDecimal amt = (price == null) ? BigDecimal.ZERO : newQty.multiply(price).setScale(2, RoundingMode.HALF_UP);
        f.setForecastQty(newQty);
        f.setForecastAmt(amt);
        f.setMethod("MANUAL");
        log.info("[adjustQty] id={} newQty={} amt={} operator={}", forecastId, newQty, amt, operator);
        return mapper.updateById(f) > 0;
    }

    /* ============ 看板 ============ */
    @Override
    public Map<String, Object> kanbanSummary(Long customerId) {
        Map<String, Object> r = new LinkedHashMap<>();
        String c = customerId == null ? "" : " AND customer_id = " + customerId;
        // 取最近生效版本(最大 version_no)
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT version_no AS \"versionNo\", " +
                        "COALESCE(SUM(forecast_qty),0) AS \"forecastQty\", " +
                        "COALESCE(SUM(forecast_amt),0) AS \"forecastAmt\", " +
                        "COALESCE(SUM(actual_qty),0) AS \"actualQty\", " +
                        "COALESCE(SUM(actual_amt),0) AS \"actualAmt\", " +
                        "COUNT(*) AS \"rowsCount\", " +
                        "SUM(CASE WHEN forecast_qty > 0 THEN 1 ELSE 0 END) AS \"forecastRows\" " +
                        "FROM sal_forecast WHERE status = 1" + c +
                        " GROUP BY version_no ORDER BY version_no DESC LIMIT 1");
        if (rows.isEmpty()) {
            r.put("versionNo", "");
            r.put("forecastQty", BigDecimal.ZERO);
            r.put("forecastAmt", BigDecimal.ZERO);
            r.put("actualQty", BigDecimal.ZERO);
            r.put("actualAmt", BigDecimal.ZERO);
            r.put("hitRate", 0.0);
            r.put("rowsCount", 0);
            return r;
        }
        Map<String, Object> row = rows.get(0);
        BigDecimal fcQty = dec(row.get("forecastQty"));
        BigDecimal acQty = dec(row.get("actualQty"));
        double hitRate = (fcQty.signum() == 0) ? 0 :
                acQty.divide(fcQty, 4, RoundingMode.HALF_UP).doubleValue();
        r.put("versionNo", row.get("versionNo"));
        r.put("forecastQty", fcQty);
        r.put("forecastAmt", dec(row.get("forecastAmt")));
        r.put("actualQty", acQty);
        r.put("actualAmt", dec(row.get("actualAmt")));
        r.put("hitRate", hitRate);
        r.put("rowsCount", num(row.get("rowsCount")));
        r.put("forecastRows", num(row.get("forecastRows")));
        return r;
    }

    @Override
    public List<Map<String, Object>> forecastTrend(Long customerId, int periods) {
        int n = Math.max(1, periods);
        String c = customerId == null ? "" : " AND customer_id = " + customerId;
        // 取最近生效版本 + 按周期分桶
        return jdbcTemplate.queryForList(
                "SELECT period_start AS \"periodStart\", period_end AS \"periodEnd\", " +
                        "COALESCE(SUM(forecast_qty),0) AS \"forecastQty\", " +
                        "COALESCE(SUM(forecast_amt),0) AS \"forecastAmt\", " +
                        "COALESCE(SUM(actual_qty),0) AS \"actualQty\", " +
                        "COALESCE(SUM(actual_amt),0) AS \"actualAmt\" " +
                        "FROM sal_forecast WHERE status = 1" + c +
                        " GROUP BY period_start, period_end ORDER BY period_start ASC LIMIT " + n);
    }

    @Override
    public List<Map<String, Object>> topCustomers(Long customerId, int limit) {
        String c = customerId == null ? "" : " AND customer_id = " + customerId;
        return jdbcTemplate.queryForList(
                "SELECT customer_id AS \"customerId\", MAX(customer_name) AS \"customerName\", " +
                        "COALESCE(SUM(forecast_qty),0) AS \"forecastQty\", " +
                        "COALESCE(SUM(forecast_amt),0) AS \"forecastAmt\", " +
                        "COALESCE(SUM(actual_qty),0) AS \"actualQty\" " +
                        "FROM sal_forecast WHERE status = 1 AND customer_id IS NOT NULL" + c +
                        " GROUP BY customer_id ORDER BY \"forecastAmt\" DESC LIMIT " + Math.max(1, limit));
    }

    @Override
    public List<Map<String, Object>> topMaterials(Long customerId, int limit) {
        String c = customerId == null ? "" : " AND customer_id = " + customerId;
        return jdbcTemplate.queryForList(
                "SELECT material_id AS \"materialId\", MAX(material_code) AS \"materialCode\", " +
                        "MAX(material_name) AS \"materialName\", " +
                        "COALESCE(SUM(forecast_qty),0) AS \"forecastQty\", " +
                        "COALESCE(SUM(actual_qty),0) AS \"actualQty\" " +
                        "FROM sal_forecast WHERE status = 1 AND material_id IS NOT NULL" + c +
                        " GROUP BY material_id ORDER BY \"forecastQty\" DESC LIMIT " + Math.max(1, limit));
    }

    /* ============ 定时任务: 每周一凌晨1:05自动滚动 ============ */
    @Scheduled(cron = "0 5 1 ? * MON")
    public void autoRollWeekly() {
        try {
            int rows = generateVersion("W", DEFAULT_PERIODS, "system-scheduler");
            log.info("[autoRollWeekly] 周版本自动滚动完成, 生成 {} 行", rows);
        } catch (Exception e) {
            log.error("[autoRollWeekly] 周版本自动滚动失败: {}", e.getMessage(), e);
        }
    }

    /* ============ 工具 ============ */
    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private String toStr(Object o) { return o == null ? "" : String.valueOf(o); }
    private long num(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
    private BigDecimal dec(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}
