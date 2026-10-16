package com.pcba.erp.sal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.entity.PdmBom;
import com.pcba.erp.pdm.entity.PdmBomLine;
import com.pcba.erp.pdm.service.IPdmBomService;
import com.pcba.erp.sal.entity.SalKitting;
import com.pcba.erp.sal.entity.SalKittingLine;
import com.pcba.erp.sal.entity.SalSalesOrder;
import com.pcba.erp.sal.entity.SalSalesOrderLine;
import com.pcba.erp.sal.mapper.SalKittingLineMapper;
import com.pcba.erp.sal.mapper.SalKittingMapper;
import com.pcba.erp.sal.service.ISalKittingService;
import com.pcba.erp.sal.service.ISalSalesOrderService;
import com.pcba.erp.scm.entity.ScmPurchaseRequisition;
import com.pcba.erp.scm.entity.ScmPurchaseRequisitionLine;
import com.pcba.erp.scm.service.IScmPurchaseRequisitionLineService;
import com.pcba.erp.scm.service.IScmPurchaseRequisitionService;
import com.pcba.erp.wms.entity.WmsStockOut;
import com.pcba.erp.wms.entity.WmsStockOutLine;
import com.pcba.erp.wms.service.IWmsStockOutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 销售齐套 Service 实现
 * - 核心: runKittingCheck 展开 BOM×套数 → 查库存 → 生成明细
 * - generateStockOut: 齐套完成 → 生成 WMS 出库单
 * - generatePurchaseRequisition: 缺料 → 生成 SCM 请购单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalKittingServiceImpl implements ISalKittingService {

    private final SalKittingMapper kittingMapper;
    private final SalKittingLineMapper lineMapper;
    private final JdbcTemplate jdbcTemplate;
    @Lazy private final IPdmBomService bomService;
    @Lazy private final IWmsStockOutService wmsStockOutService;
    @Lazy private final IScmPurchaseRequisitionService requisitionService;
    @Lazy private final IScmPurchaseRequisitionLineService requisitionLineService;
    @Lazy private final ISalSalesOrderService salesOrderService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ============ CRUD ============

    @Override
    public IPage<SalKitting> selectPage(int pageNum, int pageSize,
                                         String kittingNo, String soNo,
                                         Long customerId, Integer status, Long bomId) {
        Page<SalKitting> page = new Page<>(pageNum, pageSize);
        return kittingMapper.selectPage(page, kittingNo, soNo, customerId, status, bomId);
    }

    @Override
    public SalKitting getDetail(Long id) {
        SalKitting k = kittingMapper.selectById(id);
        if (k == null) throw new ServiceException("齐套单不存在");
        k.setLines(lineMapper.selectByKittingId(id));
        return k;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SalKitting entity) {
        if (entity.getStatus() == null) entity.setStatus(0);
        if (entity.getKitQty() == null) entity.setKitQty(BigDecimal.ONE);
        if (entity.getKittingNo() == null || entity.getKittingNo().isEmpty()) {
            entity.setKittingNo(nextKittingNo());
        } else {
            int dup = kittingMapper.countDuplicate(entity.getKittingNo(), null);
            if (dup > 0) throw new ServiceException("齐套单号【" + entity.getKittingNo() + "】已存在");
        }
        // 冗余BOM信息
        fillBomInfo(entity);
        if (kittingMapper.insert(entity) <= 0) throw new ServiceException("新增齐套单失败");
        // 保存明细(如果有)
        if (entity.getLines() != null && !entity.getLines().isEmpty()) {
            int lineNo = 1;
            for (SalKittingLine line : entity.getLines()) {
                line.setKittingId(entity.getKittingId());
                line.setLineNo(lineNo++);
                lineMapper.insert(line);
            }
        }
        return entity.getKittingId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SalKitting entity) {
        if (entity.getKittingId() == null) throw new ServiceException("齐套单ID不能为空");
        SalKitting old = kittingMapper.selectById(entity.getKittingId());
        if (old == null) throw new ServiceException("齐套单不存在");
        Integer s = old.getStatus();
        if (!Integer.valueOf(0).equals(s) && !Integer.valueOf(5).equals(s)) {
            throw new ServiceException("当前状态不允许修改");
        }
        if (entity.getKittingNo() != null) {
            int dup = kittingMapper.countDuplicate(entity.getKittingNo(), entity.getKittingId());
            if (dup > 0) throw new ServiceException("齐套单号已存在");
        }
        fillBomInfo(entity);
        return kittingMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        SalKitting k = kittingMapper.selectById(id);
        if (k == null) throw new ServiceException("齐套单不存在");
        Integer s = k.getStatus();
        if (!Integer.valueOf(0).equals(s) && !Integer.valueOf(5).equals(s)) {
            throw new ServiceException("当前状态不允许删除");
        }
        lineMapper.deleteByKittingId(id);
        return kittingMapper.deleteById(id) > 0;
    }

    @Override
    public List<SalKitting> options() {
        return kittingMapper.selectList(
                new LambdaQueryWrapper<SalKitting>()
                        .orderByDesc(SalKitting::getKittingId)
                        .last("LIMIT 200"));
    }

    @Override
    public String nextKittingNo() {
        String date = LocalDateTime.now().format(FMT);
        String prefix = "SK-" + date + "-";
        SalKitting last = kittingMapper.selectOne(
                new LambdaQueryWrapper<SalKitting>()
                        .likeRight(SalKitting::getKittingNo, prefix)
                        .orderByDesc(SalKitting::getKittingId)
                        .last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getKittingNo() != null) {
            try {
                String tail = last.getKittingNo().substring(prefix.length());
                seq = Integer.parseInt(tail) + 1;
            } catch (Exception ignored) { }
        }
        return prefix + String.format("%04d", seq);
    }

    // ============ 核心: 齐套检查 ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean runKittingCheck(Long kittingId) {
        SalKitting k = requireStatus(kittingId, 0, 1, 5); // 待检查/部分齐套/已取消可重新检查

        // 校验BOM
        PdmBom bom = bomService.getById(k.getBomId());
        if (bom == null) throw new ServiceException("BOM不存在");
        if (bom.getBomType() == null || bom.getBomType() != 3) {
            throw new ServiceException("BOM类型不是销售BOM(bomType=3)");
        }
        if (bom.getStatus() == null || bom.getStatus() != 2) {
            throw new ServiceException("BOM未启用(status=2)");
        }

        // BOM扁平化展开 → 叶子物料累计用量
        Map<Long, PdmBomLine> flatSum = bomService.explodeFlatSum(k.getBomId(), 10, true);
        if (flatSum == null || flatSum.isEmpty()) {
            throw new ServiceException("BOM展开后无子件，请先维护BOM行项");
        }

        BigDecimal kitQty = k.getKitQty() == null ? BigDecimal.ONE : k.getKitQty();

        // 删旧行
        lineMapper.deleteByKittingId(kittingId);

        // 逐物料: 计算需求 → 查库存 → 判断缺料
        List<SalKittingLine> newLines = new ArrayList<>();
        int lineNo = 1;
        BigDecimal totalReq = BigDecimal.ZERO;
        BigDecimal totalAvail = BigDecimal.ZERO;
        BigDecimal totalShort = BigDecimal.ZERO;
        int shortageCount = 0;

        for (Map.Entry<Long, PdmBomLine> entry : flatSum.entrySet()) {
            Long materialId = entry.getKey();
            PdmBomLine bl = entry.getValue();

            BigDecimal pathQty = bl.getPathQty() != null ? bl.getPathQty() : BigDecimal.ZERO;
            BigDecimal reqQty = pathQty.multiply(kitQty).setScale(4, BigDecimal.ROUND_HALF_UP);

            // 查可用库存(跨仓汇总)
            BigDecimal available = kittingMapper.sumAvailableByMaterial(materialId);
            if (available == null) available = BigDecimal.ZERO;

            BigDecimal shortage = reqQty.subtract(available);
            if (shortage.compareTo(BigDecimal.ZERO) < 0) shortage = BigDecimal.ZERO;
            boolean isShort = shortage.compareTo(BigDecimal.ZERO) > 0;

            // FIFO建议批次
            String batchNo = pickAvailableBatch(k.getWarehouseId(), materialId);

            SalKittingLine line = new SalKittingLine();
            line.setKittingId(kittingId);
            line.setLineNo(lineNo++);
            line.setMaterialId(materialId);
            line.setMaterialCode(bl.getComponentMaterialCode());
            line.setMaterialName(bl.getComponentMaterialName());
            line.setSpecification(bl.getComponentSpec());
            line.setUnit(bl.getUnit());
            line.setBomLineId(bl.getLineId());
            line.setReqQty(reqQty);
            line.setAvailableQty(available);
            line.setShortageQty(shortage);
            line.setWarehouseId(k.getWarehouseId());
            line.setBatchNo(batchNo);
            line.setIsShortage(isShort ? 1 : 0);
            line.setLineStatus(isShort ? 2 : 1);
            lineMapper.insert(line);
            newLines.add(line);

            totalReq = totalReq.add(reqQty);
            totalAvail = totalAvail.add(available);
            totalShort = totalShort.add(shortage);
            if (isShort) shortageCount++;
        }

        // 更新主表汇总
        k.setTotalLines(newLines.size());
        k.setTotalRequiredQty(totalReq);
        k.setTotalAvailableQty(totalAvail);
        k.setTotalShortageQty(totalShort);
        k.setCheckTime(LocalDateTime.now());
        // 状态推进: 无缺料→2齐套完成, 有缺料→1部分齐套
        k.setStatus(shortageCount == 0 ? 2 : 1);

        return kittingMapper.updateById(k) > 0;
    }

    // ============ 生成出库单 ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateStockOut(Long kittingId) {
        SalKitting k = requireStatus(kittingId, 2); // 齐套完成
        if (k.getStockOutId() != null) {
            throw new ServiceException("已生成出库单(stockOutId=" + k.getStockOutId() + ")，不可重复生成");
        }

        List<SalKittingLine> lines = lineMapper.selectByKittingId(kittingId);
        if (lines == null || lines.isEmpty()) {
            throw new ServiceException("齐套明细为空，请先执行齐套检查");
        }

        // 构造出库主单
        Long warehouseId = k.getWarehouseId();
        if (warehouseId == null) {
            warehouseId = lines.stream().map(SalKittingLine::getWarehouseId).filter(Objects::nonNull).findFirst().orElse(1L);
        }

        WmsStockOut stockOut = new WmsStockOut();
        stockOut.setWarehouseId(warehouseId);
        stockOut.setOutType("SALES");
        stockOut.setCustomerId(k.getCustomerId());
        stockOut.setCustomerName(k.getCustomerName());
        stockOut.setRemark("来自销售齐套: " + k.getKittingNo());

        // 构造出库明细行
        List<WmsStockOutLine> outLines = new ArrayList<>();
        for (SalKittingLine line : lines) {
            if (line.getIsShortage() != null && line.getIsShortage() == 1) continue; // 跳过缺料行
            WmsStockOutLine sl = new WmsStockOutLine();
            sl.setSoId(k.getSoId());
            sl.setSoLineId(k.getSoLineId());
            sl.setMaterialId(line.getMaterialId());
            sl.setMaterialCode(line.getMaterialCode());
            sl.setMaterialName(line.getMaterialName());
            sl.setSpecification(line.getSpecification());
            sl.setUnit(line.getUnit());
            sl.setQuantity(line.getReqQty());
            sl.setBatchNo(line.getBatchNo() != null ? line.getBatchNo() : "");
            outLines.add(sl);
        }
        if (outLines.isEmpty()) {
            throw new ServiceException("无齐套明细可出库(全部缺料)");
        }
        stockOut.setLines(outLines);

        Long stockOutId = wmsStockOutService.saveStockOut(stockOut);
        // 自动推进出库单: 草稿0 → 提交1 → 审核2 → 确认出库3(扣减库存台账)，保证P2的库存效果
        try {
            wmsStockOutService.submitAudit(stockOutId);
            wmsStockOutService.auditPass(stockOutId);
            wmsStockOutService.confirmStockOut(stockOutId);
        } catch (Exception ex) {
            log.error("[generateStockOut] 自动确认出库失败 kitting={} stockOutId={}", kittingId, stockOutId, ex);
            throw new ServiceException("生成出库单后确认出库失败: " + ex.getMessage());
        }
        k.setStockOutId(stockOutId);
        k.setCompleteTime(LocalDateTime.now());
        k.setStatus(3); // 已生成出库
        kittingMapper.updateById(k);
        log.info("[generateStockOut] kitting={} → WMS出库单 stockOutId={} status→3", kittingId, stockOutId);
        return stockOutId;
    }

    // ============ 生成请购单 (T5-1 重写: 缺料实时计算 + 追加/复用当日本地请购草稿) ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generatePurchaseRequisition(Long kittingId) {
        log.debug("[generatePurchaseRequisition] begin kittingId={}", kittingId);
        // 1. 取 kitting+kittingLines；status 为 1/2（已保存且已执行过齐套检查或齐套完成）
        SalKitting k = requireStatus(kittingId, 1, 2);

        LocalDate requireDate = k.getCheckTime() != null
                ? k.getCheckTime().toLocalDate().plusDays(14)
                : LocalDate.now().plusDays(14);
        // 申请人：优先取齐套创建人，其次 operatorId，否则 1L
        Long applicantId = 1L;
        String applicantName = null;
        try {
            if (k.getCreateBy() != null && !k.getCreateBy().isBlank()) {
                applicantName = k.getCreateBy();
            } else if (k.getOperatorName() != null) {
                applicantName = k.getOperatorName();
            }
            if (k.getOperatorId() != null) {
                applicantId = k.getOperatorId();
            }
        } catch (Exception ignored) {}

        List<SalKittingLine> lines = lineMapper.selectByKittingId(kittingId);
        if (lines == null || lines.isEmpty()) {
            throw new ServiceException("齐套明细为空，请先执行齐套检查");
        }

        // 2. 逐物料计算 deficit
        java.util.LinkedHashMap<Long, BigDecimal> deficitMap = new java.util.LinkedHashMap<>();
        for (SalKittingLine line : lines) {
            Long materialId = line.getMaterialId();
            BigDecimal requiredQty = line.getReqQty() == null ? BigDecimal.ZERO : line.getReqQty();
            BigDecimal available = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(i.quantity - i.locked_qty),0) FROM wms_inventory i " +
                            "LEFT JOIN wms_warehouse w ON i.warehouse_id = w.warehouse_id " +
                            "WHERE i.material_id = ? AND (w.warehouse_type IS NULL OR w.warehouse_type != 'IQC')",
                    BigDecimal.class, materialId);
            if (available == null) available = BigDecimal.ZERO;

            BigDecimal onOrderRemaining = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(l.order_qty - COALESCE(l.received_qty,0) - COALESCE(l.returned_qty,0)),0) " +
                            "FROM scm_purchase_order_line l JOIN scm_purchase_order o ON o.po_id=l.po_id " +
                            "WHERE o.status IN (1,2,3,4) AND l.material_id = ?",
                    BigDecimal.class, materialId);
            if (onOrderRemaining == null) onOrderRemaining = BigDecimal.ZERO;

            BigDecimal deficit = requiredQty.subtract(available).subtract(onOrderRemaining);
            if (deficit.compareTo(BigDecimal.ZERO) < 0) deficit = BigDecimal.ZERO;
            if (deficit.compareTo(BigDecimal.ZERO) > 0) {
                deficitMap.merge(materialId, deficit, BigDecimal::add);
            }
        }

        if (deficitMap.isEmpty()) {
            throw new ServiceException("无缺料明细，不需要生成请购单");
        }

        // 3. 复用或创建 requisition：当天 status∈{0,1} 的草稿→追加；否则新建
        LocalDate today = LocalDate.now();
        Long finalApplicantId = applicantId;
        ScmPurchaseRequisition exist = requisitionService.options().stream()
                .filter(r -> today.equals(r.getRequisitionDate()))
                .filter(r -> {
                    Integer s = r.getStatus();
                    return Integer.valueOf(0).equals(s) || Integer.valueOf(1).equals(s);
                })
                .filter(r -> finalApplicantId == null || r.getApplicantId() == null || r.getApplicantId().equals(finalApplicantId) || true)
                .findFirst()
                .orElse(null);

        Long requisitionId;
        if (exist != null) {
            requisitionId = exist.getRequisitionId();
            log.info("[generatePurchaseRequisition] 追加到已存在的当日请购草稿 requisitionId={}", requisitionId);
        } else {
            // 原材料主仓（非 IQC）
            Long rawWhId = k.getWarehouseId();
            if (rawWhId == null) {
                try {
                    List<Long> ids = jdbcTemplate.queryForList(
                            "SELECT warehouse_id FROM wms_warehouse WHERE warehouse_type IS NOT NULL AND warehouse_type != 'IQC' LIMIT 1",
                            Long.class);
                    if (ids != null && !ids.isEmpty()) rawWhId = ids.get(0);
                } catch (Exception ignore) {}
            }
            ScmPurchaseRequisition pr = new ScmPurchaseRequisition();
            pr.setTitle("齐套缺料单 #" + (k.getKittingNo() == null ? "" : k.getKittingNo()));
            pr.setReason("来自销售齐套缺料: " + (k.getKittingNo() == null ? "" : k.getKittingNo()));
            pr.setWarehouseId(rawWhId);
            pr.setRequisitionDate(today);
            pr.setRequiredDate(requireDate);
            pr.setApplicantId(applicantId);
            pr.setApplicantName(applicantName);
            pr.setCurrency("CNY");
            pr.setTaxRate(new BigDecimal("13"));
            pr.setStatus(0);
            requisitionId = requisitionService.save(pr);
            log.info("[generatePurchaseRequisition] 新建请购单 requisitionId={}", requisitionId);
        }

        // 4. 聚合同 requisition 内已有同 materialId/requiredDate 行：deficit 累加
        List<ScmPurchaseRequisitionLine> existingLines = requisitionLineService.options().stream()
                .filter(l -> requisitionId.equals(l.getRequisitionId()))
                .collect(java.util.stream.Collectors.toList());

        java.util.Map<String, ScmPurchaseRequisitionLine> existingLineKey = new java.util.HashMap<>();
        for (ScmPurchaseRequisitionLine l : existingLines) {
            String key = l.getMaterialId() + "_" + (l.getRequiredDate() == null ? "" : l.getRequiredDate().toString());
            existingLineKey.put(key, l);
        }

        // 取缺料物料信息
        java.util.Map<Long, com.pcba.erp.pdm.entity.PdmMaterial> materialMap = new java.util.HashMap<>();
        try {
            for (Long mid : deficitMap.keySet()) {
                com.pcba.erp.pdm.entity.PdmMaterial m = jdbcTemplate.queryForObject(
                        "SELECT material_id, material_code, material_name, specification, unit, min_order_qty FROM pdm_material WHERE material_id=?",
                        (rs, rn) -> {
                            com.pcba.erp.pdm.entity.PdmMaterial m0 = new com.pcba.erp.pdm.entity.PdmMaterial();
                            m0.setMaterialId(rs.getLong("material_id"));
                            m0.setMaterialCode(rs.getString("material_code"));
                            m0.setMaterialName(rs.getString("material_name"));
                            m0.setSpecification(rs.getString("specification"));
                            m0.setUnit(rs.getString("unit"));
                            m0.setMinOrderQty((BigDecimal) rs.getObject("min_order_qty"));
                            return m0;
                        }, mid);
                if (m != null) materialMap.put(mid, m);
            }
        } catch (Exception ignore) {}

        for (SalKittingLine line : lines) {
            Long materialId = line.getMaterialId();
            BigDecimal deficit = deficitMap.get(materialId);
            if (deficit == null || deficit.compareTo(BigDecimal.ZERO) <= 0) continue;

            String key = materialId + "_" + requireDate.toString();
            ScmPurchaseRequisitionLine existLine = existingLineKey.get(key);
            if (existLine != null) {
                BigDecimal newQty = existLine.getQty() == null ? BigDecimal.ZERO : existLine.getQty();
                newQty = newQty.add(deficit);
                existLine.setQty(newQty);
                requisitionLineService.update(existLine);
            } else {
                ScmPurchaseRequisitionLine prLine = new ScmPurchaseRequisitionLine();
                prLine.setRequisitionId(requisitionId);
                prLine.setLineNo(existingLineKey.size() + 1);
                prLine.setMaterialId(materialId);
                prLine.setMaterialCode(line.getMaterialCode());
                prLine.setMaterialName(line.getMaterialName());
                prLine.setSpecification(line.getSpecification());
                prLine.setUnit(line.getUnit());
                prLine.setQty(deficit);
                prLine.setRequiredDate(requireDate);
                prLine.setStatus(0);
                prLine.setRemark("齐套 " + (k.getKittingNo() == null ? "" : k.getKittingNo()));
                requisitionLineService.save(prLine);
                existingLineKey.put(key, prLine);
            }

            // 处理完即从 deficitMap 移除，避免多明细行重复累加
            deficitMap.remove(materialId);
        }

        // 回填 kitting.requisitionId + status
        k.setRequisitionId(requisitionId);
        if (k.getStatus() != null && k.getStatus() < 4) {
            k.setStatus(4); // 已生成请购
        }
        kittingMapper.updateById(k);
        log.debug("[generatePurchaseRequisition] done kitting={} requisitionId={}", kittingId, requisitionId);
        return requisitionId;
    }

    // ============ 取消 ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelKitting(Long id) {
        SalKitting k = requireStatus(id, 0, 1); // 待检查/部分齐套
        k.setStatus(5); // 已取消
        return kittingMapper.updateById(k) > 0;
    }

    // ============ P0: 从订单行创建齐套单 ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalKitting createFromOrderLine(Long soLineId, Long bomIdOverride) {
        // 1. 订单 + 订单行
        SalSalesOrderLine sol = jdbcTemplate.queryForObject(
                "SELECT * FROM sal_sales_order_line WHERE line_id=?",
                (rs, rn) -> {
                    SalSalesOrderLine l = new SalSalesOrderLine();
                    l.setLineId(rs.getLong("line_id"));
                    l.setSoId(rs.getLong("so_id"));
                    l.setLineNo(rs.getInt("line_no"));
                    l.setMaterialId(rs.getLong("material_id"));
                    l.setMaterialCode(rs.getString("material_code"));
                    l.setMaterialName(rs.getString("material_name"));
                    l.setSpecification(rs.getString("specification"));
                    l.setUnit(rs.getString("unit"));
                    l.setOrderQty(rs.getBigDecimal("order_qty"));
                    l.setRequiredDate(rs.getDate("required_date") == null ? null : rs.getDate("required_date").toLocalDate());
                    l.setWarehouseId((Long) rs.getObject("warehouse_id"));
                    return l;
                }, soLineId);
        if (sol == null) throw new ServiceException("订单行不存在: soLineId=" + soLineId);
        SalSalesOrder so = salesOrderService.getDetail(sol.getSoId());
        if (so == null) throw new ServiceException("销售订单不存在: soId=" + sol.getSoId());

        // 2. 避免同一订单行重复创建有效齐套单(status != 5)
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sal_kitting WHERE so_line_id=? AND status<>5",
                Integer.class, soLineId);
        if (cnt != null && cnt > 0) {
            SalKitting exist = kittingMapper.selectOne(new LambdaQueryWrapper<SalKitting>()
                    .eq(SalKitting::getSoLineId, soLineId).ne(SalKitting::getStatus, 5).last("LIMIT 1"));
            if (exist != null) {
                throw new ServiceException("订单行已存在有效齐套单: kittingId=" + exist.getKittingId());
            }
        }

        // 3. 匹配销售BOM(bomType=3, status=2, productMaterialId=订单行物料, customerId=订单客户)
        //    优先客户匹配 & is_default=1, 其次通用 & 最新 create_time
        Long bomId = bomIdOverride;
        if (bomId == null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT bom_id FROM pdm_bom WHERE bom_type=3 AND status=2 AND product_material_id=? " +
                    "  AND (customer_id=? OR customer_id IS NULL) " +
                    "ORDER BY (CASE WHEN customer_id=? THEN 0 ELSE 1 END), COALESCE(is_default,0) DESC, create_time DESC LIMIT 1",
                    sol.getMaterialId(), so.getCustomerId(), so.getCustomerId());
            if (rows.isEmpty()) {
                throw new ServiceException("未匹配到该成品的已启用销售BOM(bomType=3)。请先到BOM管理创建【客户=" + so.getCustomerName()
                        + " 成品=" + sol.getMaterialCode() + "】的销售BOM。");
            }
            bomId = ((Number) rows.get(0).get("bom_id")).longValue();
        }

        // 4. 组装齐套单主档
        SalKitting k = new SalKitting();
        k.setKittingNo(nextKittingNo());
        k.setSoId(so.getSoId());
        k.setSoNo(so.getSoNo());
        k.setSoLineId(sol.getLineId());
        k.setCustomerId(so.getCustomerId());
        k.setCustomerName(so.getCustomerName());
        k.setBomId(bomId);
        k.setProductMaterialId(sol.getMaterialId());
        k.setProductMaterialCode(sol.getMaterialCode());
        k.setProductMaterialName(sol.getMaterialName());
        k.setKitQty(sol.getOrderQty());
        k.setWarehouseId(sol.getWarehouseId() != null ? sol.getWarehouseId() : so.getWarehouseId());
        k.setStatus(0);
        Long kid = save(k);

        // 5. 立即执行齐套检查，省掉手工再点一次
        runKittingCheck(kid);
        return getDetail(kid);
    }

    // ============ 辅助方法 ============

    private SalKitting requireStatus(Long id, Integer... allowed) {
        SalKitting k = kittingMapper.selectById(id);
        if (k == null) throw new ServiceException("齐套单不存在");
        if (allowed != null) {
            boolean ok = false;
            for (Integer a : allowed) if (a.equals(k.getStatus())) { ok = true; break; }
            if (!ok) throw new ServiceException("当前齐套单状态不允许此操作");
        }
        return k;
    }

    private void fillBomInfo(SalKitting entity) {
        if (entity.getBomId() == null) return;
        PdmBom bom = bomService.getById(entity.getBomId());
        if (bom != null) {
            entity.setKitModel(bom.getKitModel());
            entity.setProductMaterialId(bom.getProductMaterialId());
            entity.setProductMaterialCode(bom.getProductMaterialCode());
            entity.setProductMaterialName(bom.getProductMaterialName());
            if (entity.getCustomerId() == null) {
                entity.setCustomerId(bom.getCustomerId());
            }
        }
    }

    /**
     * FIFO 选取仓库+物料的第一个可用批次
     */
    private String pickAvailableBatch(Long warehouseId, Long materialId) {
        if (warehouseId == null || materialId == null) return "";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT batch_no FROM wms_inventory " +
                            "WHERE warehouse_id=? AND material_id=? AND available_qty > 0 " +
                            "ORDER BY inventory_id ASC LIMIT 1",
                    warehouseId, materialId);
            if (rows.isEmpty()) return "";
            Object bn = rows.get(0).get("batch_no");
            return bn == null ? "" : bn.toString();
        } catch (Exception e) {
            log.warn("[pickAvailableBatch] 查库存失败 wh={} mat={} msg={}", warehouseId, materialId, e.getMessage());
            return "";
        }
    }
}
