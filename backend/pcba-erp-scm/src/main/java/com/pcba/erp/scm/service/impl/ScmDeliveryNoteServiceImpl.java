package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.FasProductStock;
import com.pcba.erp.pmm.mapper.FasProductStockMapper;
import com.pcba.erp.scm.entity.*;
import com.pcba.erp.scm.mapper.*;
import com.pcba.erp.scm.service.IScmDeliveryNoteService;
import com.pcba.erp.scm.service.IScmPurchaseReturnLineService;
import com.pcba.erp.scm.service.IScmPurchaseReturnService;
import com.pcba.erp.wms.entity.WmsInventory;
import com.pcba.erp.wms.entity.WmsStockIn;
import com.pcba.erp.wms.entity.WmsStockInLine;
import com.pcba.erp.wms.entity.WmsWarehouse;
import com.pcba.erp.wms.mapper.WmsInventoryMapper;
import com.pcba.erp.wms.mapper.WmsStockInMapper;
import com.pcba.erp.wms.mapper.WmsWarehouseMapper;
import com.pcba.erp.wms.service.IWmsInventoryService;
import com.pcba.erp.wms.service.IWmsStockInService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScmDeliveryNoteServiceImpl implements IScmDeliveryNoteService {

    private final ScmDeliveryNoteMapper mapper;
    private final ScmDeliveryNoteLineMapper lineMapper;
    private final ScmPurchaseOrderMapper poMapper;
    private final ScmPurchaseOrderLineMapper poLineMapper;
    private final FasProductStockMapper fasStockMapper;
    private final IWmsStockInService stockInService;
    /* ============ QC分流新增依赖 ============ */
    private final WmsWarehouseMapper warehouseMapper;
    private final IWmsInventoryService inventoryService;
    private final IScmPurchaseReturnService purchaseReturnService;
    private final ScmPurchaseReturnLineMapper returnLineMapper;
    private final WmsStockInMapper wmsStockInMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public IPage<ScmDeliveryNote> selectPage(int pageNum, int pageSize, String deliveryNo, String poNo, Long supplierId, String supplierName, Long warehouseId, Integer status, String deliveryDate) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), deliveryNo, poNo, supplierId, supplierName, warehouseId, status, deliveryDate);
    }

    @Override
    public ScmDeliveryNote getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        ScmDeliveryNote r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScmDeliveryNote entity) {
        // 单号兜底
        try {
            Field f = ScmDeliveryNote.class.getDeclaredField("deliveryNo");
            f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank()) {
                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                f.set(entity, "DN" + ts);
            }
        } catch (Exception ignore) {}
        // 到货日期
        try {
            Field f = ScmDeliveryNote.class.getDeclaredField("deliveryDate");
            f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, LocalDate.now());
        } catch (Exception ignore) {}
        // 仓库默认 1
        try {
            Field f = ScmDeliveryNote.class.getDeclaredField("warehouseId");
            f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, 1L);
        } catch (Exception ignore) {}
        // 单号查重 (若实体有"单号"字段且非空)
        try {
            Field noField = ScmDeliveryNote.class.getDeclaredField("deliveryNo");
            noField.setAccessible(true);
            Object noVal = noField.get(entity);
            if (noVal != null && !noVal.toString().isBlank()) {
                Field idField = ScmDeliveryNote.class.getDeclaredField("deliveryId");
                idField.setAccessible(true);
                Object idVal = idField.get(entity);
                if (mapper.countDuplicate(noVal.toString(), (Long) idVal) > 0) {
                    throw new ServiceException("编号已存在: " + noVal);
                }
            }
        } catch (NoSuchFieldException ignore) { /* 无对应字段跳过查重 */ }
          catch (IllegalAccessException ex) { throw new ServiceException("保存失败: " + ex.getMessage()); }

        // 默认值: status=0 / 数字 0
        try {
            Field s = ScmDeliveryNote.class.getDeclaredField("status");
            s.setAccessible(true);
            if (s.get(entity) == null) s.set(entity, 0);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        for (String f : new String[]{"totalAmount","taxAmount","paidAmount","totalQty","refundAmount","qty","estimatePrice","estimateAmount","orderQty","receivedQty","returnedQty","unitPrice","lineAmount","taxRate","minQty","discountRate"}) {
            try {
                Field ff = ScmDeliveryNote.class.getDeclaredField(f);
                ff.setAccessible(true);
                if (ff.get(entity) == null && ff.getType() == BigDecimal.class) ff.set(entity, BigDecimal.ZERO);
            } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        }
        mapper.insert(entity);
        try {
            Field idF = ScmDeliveryNote.class.getDeclaredField("deliveryId");
            idF.setAccessible(true);
            return (Long) idF.get(entity);
        } catch (Exception ex) { return null; }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ScmDeliveryNote entity) {
        Long id = null;
        try {
            Field idF = ScmDeliveryNote.class.getDeclaredField("deliveryId");
            idF.setAccessible(true);
            id = (Long) idF.get(entity);
        } catch (Exception ignore) {}
        if (id == null) throw new ServiceException("ID不能为空");
        ScmDeliveryNote old = mapper.selectById(id);
        if (old == null) throw new ServiceException("记录不存在");
        // 单号查重
        try {
            Field noField = ScmDeliveryNote.class.getDeclaredField("deliveryNo");
            noField.setAccessible(true);
            Object noVal = noField.get(entity);
            if (noVal != null && !noVal.toString().isBlank()) {
                if (mapper.countDuplicate(noVal.toString(), id) > 0)
                    throw new ServiceException("编号已存在");
            }
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        ScmDeliveryNote r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer status) {
        if (id == null) throw new ServiceException("ID不能为空");
        if (status == null) throw new ServiceException("状态值非法");
        ScmDeliveryNote r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        try {
            Field s = ScmDeliveryNote.class.getDeclaredField("status");
            s.setAccessible(true);
            s.set(r, status);
        } catch (Exception ignore) { throw new ServiceException("状态字段缺失"); }
        return mapper.updateById(r) > 0;
    }

    @Override
    public List<ScmDeliveryNote> options() {
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScmDeliveryNote>()
                .orderByDesc("delivery_id"));
    }

    /* ========== 转单：到货单 → 入库草稿 (按 biz_category 路由: ELECTRONIC→WMS电子仓 / ACCESSORY→配件仓 / SPARE→暂未接入) ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateStockIn(Long deliveryId) {
        if (deliveryId == null) throw new ServiceException("到货单ID不能为空");
        ScmDeliveryNote delivery = mapper.selectById(deliveryId);
        if (delivery == null) throw new ServiceException("到货单不存在");
        if (delivery.getStockInId() != null) throw new ServiceException("该到货单已生成入库单，不可重复生成");
        // 状态必须 ≥ 2 （已收货 / IQC检验中）
        if (delivery.getStatus() == null || delivery.getStatus() < 2) {
            throw new ServiceException("请先完成收货登记再生成入库单");
        }
        if (delivery.getWarehouseId() == null) delivery.setWarehouseId(1L);

        List<ScmDeliveryNoteLine> lines = lineMapper.selectList(
                new LambdaQueryWrapper<ScmDeliveryNoteLine>().eq(ScmDeliveryNoteLine::getDeliveryId, deliveryId).orderByAsc(ScmDeliveryNoteLine::getLineNo));
        if (lines == null || lines.isEmpty()) throw new ServiceException("到货单明细为空，无法生成入库单");

        // ===== 确定 biz_category：自身优先，其次回源 PO =====
        String biz = delivery.getBizCategory() == null ? "" : delivery.getBizCategory().trim().toUpperCase();
        if (biz.isEmpty() && delivery.getPoId() != null) {
            ScmPurchaseOrder po = poMapper.selectById(delivery.getPoId());
            if (po != null && po.getBizCategory() != null && !po.getBizCategory().isBlank()) {
                biz = po.getBizCategory().trim().toUpperCase();
                delivery.setBizCategory(biz); // 回填冗余，后续路由更直接
            }
        }
        if (biz.isEmpty()) biz = "ELECTRONIC"; // 兜底，保证老数据默认走电子仓

        // ===== 路由分支 =====
        Long newStockId;
        switch (biz) {
            case "ACCESSORY":
                newStockId = generateAccessoryStockIn(delivery, lines);
                break;
            case "SPARE":
                throw new ServiceException("备品备件(SPARE)采购入库流程暂未接入，请联系管理员先补齐备品仓业务模块。");
            case "ELECTRONIC":
            default:
                newStockId = generateElectronicWmsStockIn(delivery, lines);
                break;
        }
        // 回填到货单
        delivery.setStockInId(newStockId);
        delivery.setStatus(4); // 4=入库完成
        mapper.updateById(delivery);
        return newStockId;
    }

    /** ELECTRONIC/包辅：走 WMS 电子仓采购入库标准流程 */
    private Long generateElectronicWmsStockIn(ScmDeliveryNote delivery, List<ScmDeliveryNoteLine> lines) {
        WmsStockIn stockIn = new WmsStockIn();
        stockIn.setWarehouseId(delivery.getWarehouseId());
        stockIn.setInType("PURCHASE");
        stockIn.setSupplierId(delivery.getSupplierId());
        stockIn.setSupplierName(delivery.getSupplierName());
        stockIn.setRemark("来自到货单: " + (delivery.getDeliveryNo() == null ? delivery.getDeliveryId() : delivery.getDeliveryNo()));
        List<WmsStockInLine> siLines = new ArrayList<>(lines.size());
        for (ScmDeliveryNoteLine dl : lines) {
            WmsStockInLine sl = new WmsStockInLine();
            sl.setDeliveryId(delivery.getDeliveryId());
            sl.setDeliveryLineId(dl.getLineId());
            sl.setPoLineId(dl.getPoLineId());
            sl.setPoId(dl.getPoLineId() == null ? delivery.getPoId() : null);
            sl.setMaterialId(dl.getMaterialId());
            sl.setMaterialCode(dl.getMaterialCode());
            sl.setMaterialName(dl.getMaterialName());
            sl.setSpecification(dl.getSpecification());
            sl.setUnit(dl.getUnit());
            sl.setBatchNo(dl.getBatchNo());
            BigDecimal qty = dl.getAcceptedQty();
            if (qty == null || BigDecimal.ZERO.compareTo(qty) == 0) qty = dl.getDeliveryQty();
            if (qty == null) qty = BigDecimal.ZERO;
            sl.setQuantity(qty);
            sl.setRemark(dl.getRemark());
            siLines.add(sl);
        }
        stockIn.setLines(siLines);
        return stockInService.saveStockIn(stockIn);
    }

    /** ACCESSORY 外购配件：轻量写入 fas_product_stock (stock_type=ACC, doc_type=INBOUND) 生成配件仓入库草稿 + 回写 source */
    private Long generateAccessoryStockIn(ScmDeliveryNote delivery, List<ScmDeliveryNoteLine> lines) {
        String docPrefix = "WAI" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        // 按到货明细行拆为多条入库草稿（每个配件一行台账，方便后续独立出库/盘点）
        Long firstId = null;
        int seq = 0;
        for (ScmDeliveryNoteLine dl : lines) {
            seq += 1;
            BigDecimal qty = dl.getAcceptedQty();
            if (qty == null || BigDecimal.ZERO.compareTo(qty) == 0) qty = dl.getDeliveryQty();
            if (qty == null) qty = BigDecimal.ZERO;
            int qtyInt = qty.intValue();
            FasProductStock s = new FasProductStock();
            s.setProductId(dl.getMaterialId());
            s.setProductName(dl.getMaterialName() == null ? "配件-" + seq : dl.getMaterialName());
            s.setBatchNo(dl.getBatchNo() == null ? (delivery.getDeliveryNo() == null ? String.valueOf(delivery.getDeliveryId()) : delivery.getDeliveryNo()) : dl.getBatchNo());
            // 仓库名：若仓库ID存在(非默认)直接拼；否则默认"ACC-配件仓"
            String wh = (delivery.getWarehouseId() != null && delivery.getWarehouseId() != 1L) ? ("ACC-" + delivery.getWarehouseId()) : "ACC-配件仓";
            s.setWarehouse(wh);
            s.setQtyIn(0);           // 审核通过后再正式入库
            s.setQtyOut(0);
            s.setQtyAvailable(0);
            s.setOrderId(delivery.getPoId());
            s.setInTime(null);
            s.setRemark((dl.getRemark() == null ? "" : dl.getRemark()) + " 【到货:" + (delivery.getDeliveryNo() == null ? delivery.getDeliveryId() : delivery.getDeliveryNo()) + "】");
            s.setDocType("INBOUND");
            s.setDocNo(docPrefix + String.format("%03d", seq));
            s.setDocStatus(0);     // 0 草稿（等配件仓管审核通过后加库存）
            s.setFlowQty(qtyInt);
            s.setSourceType("PURCHASE_IN");
            s.setStockType("ACC");
            s.setPurchaseNo(delivery.getDeliveryNo());
            fasStockMapper.insert(s);
            if (firstId == null) firstId = s.getStockId();
        }
        return firstId;
    }

    /* ===============================================================
     * 新增：从采购订单(PO)快速生成到货通知单草稿
     * =============================================================== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromPurchaseOrder(Long poId, Map<String, Object> headerExtra) {
        if (poId == null) throw new ServiceException("采购订单ID不能为空");
        ScmPurchaseOrder po = poMapper.selectById(poId);
        if (po == null) throw new ServiceException("采购订单不存在");
        if (po.getStatus() != null && po.getStatus() >= 9) throw new ServiceException("该采购单已取消，无法生成到货单");

        // 1) 组装到货单头
        ScmDeliveryNote head = new ScmDeliveryNote();
        head.setPoId(po.getPoId());
        head.setPoNo(po.getPoNo());
        head.setBizCategory(po.getBizCategory());
        head.setSupplierId(po.getSupplierId());
        head.setSupplierName(po.getSupplierName());
        head.setDeliveryDate(LocalDate.now());
        head.setWarehouseId(po.getWarehouseId() == null ? 1L : po.getWarehouseId());
        head.setStatus(0);                 // 草稿
        head.setQcRequired(0);
        head.setQcStatus(0);               // 待检
        head.setTotalQty(BigDecimal.ZERO);
        head.setTotalAmount(BigDecimal.ZERO);
        // 单号：DN-{yyyyMMdd}-{seq6位}
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "DN-" + datePart + "-";
        long seq;
        try {
            Long cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM scm_delivery_note WHERE delivery_no LIKE ?",
                    new Object[]{prefix + "%"}, Long.class);
            seq = (cnt == null ? 0 : cnt) + 1;
        } catch (Exception e) {
            seq = System.currentTimeMillis() % 1000000;
        }
        head.setDeliveryNo(prefix + String.format("%06d", seq));
        // extra 覆盖（浅拷贝，只覆盖简单字段）
        if (headerExtra != null && !headerExtra.isEmpty()) {
            for (Map.Entry<String, Object> e : headerExtra.entrySet()) {
                String k = e.getKey();
                Object v = e.getValue();
                if (v == null) continue;
                try {
                    Field f = ScmDeliveryNote.class.getDeclaredField(k);
                    f.setAccessible(true);
                    Class<?> t = f.getType();
                    if (t == Long.class || t == long.class) {
                        f.set(head, Long.valueOf(v.toString()));
                    } else if (t == Integer.class || t == int.class) {
                        f.set(head, Integer.valueOf(v.toString()));
                    } else if (t == BigDecimal.class) {
                        f.set(head, new BigDecimal(v.toString()));
                    } else if (t == LocalDate.class) {
                        f.set(head, LocalDate.parse(v.toString()));
                    } else if (t == LocalDateTime.class) {
                        f.set(head, LocalDateTime.parse(v.toString()));
                    } else if (t == String.class) {
                        f.set(head, v.toString());
                    }
                } catch (NoSuchFieldException ignore) {
                } catch (Exception ex) {
                    log.warn("[createFromPurchaseOrder] 覆盖字段失败 field={} val={} msg={}", k, v, ex.getMessage());
                }
            }
        }
        Long headId = save(head);
        if (headId == null) throw new ServiceException("保存到货单头失败");

        // 2) 组装到货单行：从PO行取 orderQty - receivedQty - returnedQty
        List<ScmPurchaseOrderLine> poLines = poLineMapper.selectList(
                new LambdaQueryWrapper<ScmPurchaseOrderLine>().eq(ScmPurchaseOrderLine::getPoId, poId).orderByAsc(ScmPurchaseOrderLine::getLineNo));
        int lineNo = 0;
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (ScmPurchaseOrderLine pol : poLines) {
            BigDecimal order = nvl(pol.getOrderQty());
            BigDecimal received = nvl(pol.getReceivedQty());
            BigDecimal returned = nvl(pol.getReturnedQty());
            BigDecimal remain = order.subtract(received).subtract(returned);
            if (remain.compareTo(BigDecimal.ZERO) <= 0) continue;
            lineNo++;
            ScmDeliveryNoteLine dl = new ScmDeliveryNoteLine();
            dl.setDeliveryId(headId);
            dl.setLineNo(lineNo);
            dl.setPoLineId(pol.getLineId());
            dl.setMaterialId(pol.getMaterialId());
            dl.setMaterialCode(pol.getMaterialCode());
            dl.setMaterialName(pol.getMaterialName());
            dl.setSpecification(pol.getSpecification());
            dl.setUnit(pol.getUnit());
            dl.setDeliveryQty(remain);
            dl.setAcceptedQty(BigDecimal.ZERO);
            dl.setRejectedQty(BigDecimal.ZERO);
            dl.setConcessionQty(BigDecimal.ZERO);
            dl.setQcStatus(pol.getQcRequired() != null && pol.getQcRequired() == 1 ? 0 : 1); // 0待检 1直通
            dl.setQcInspectedQty(BigDecimal.ZERO);
            dl.setQcAcceptedQty(BigDecimal.ZERO);
            dl.setQcConcessionQty(BigDecimal.ZERO);
            dl.setQcRejectedQty(BigDecimal.ZERO);
            dl.setQcScrappedQty(BigDecimal.ZERO);
            dl.setStockedQty(BigDecimal.ZERO);
            // 复制 qcRequired (从PO行) → 头 qcRequired = 任一行为1则头为1
            if (pol.getQcRequired() != null && pol.getQcRequired() == 1) {
                head.setQcRequired(1);
            }
            // remark 优先PO行remark
            dl.setRemark(pol.getRemark());
            // 金额
            BigDecimal unitPrice = nvl(pol.getUnitPrice());
            BigDecimal lineAmt = remain.multiply(unitPrice);
            totalQty = totalQty.add(remain);
            totalAmt = totalAmt.add(lineAmt);
            lineMapper.insert(dl);
        }
        // 3) 回写头汇总
        head.setTotalQty(totalQty);
        head.setTotalAmount(totalAmt);
        mapper.updateById(head);
        return headId;
    }

    /* ===============================================================
     * 新增：IQC QC 判定（事务）+ 调拨 + 退货草稿 + 报废
     * =============================================================== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> qcJudge(Long deliveryId, List<Map<String, Object>> judgements) {
        // -------- 0. 基础校验 --------
        if (deliveryId == null) throw new ServiceException("到货单ID不能为空");
        ScmDeliveryNote delivery = mapper.selectById(deliveryId);
        if (delivery == null) throw new ServiceException("到货单不存在");
        if (judgements == null || judgements.isEmpty()) {
            throw new ServiceException("判定明细不能为空");
        }
        // 全部 delivery 行（用于归属校验 + 汇总头qcStatus）
        List<ScmDeliveryNoteLine> allLines = lineMapper.selectList(
                new LambdaQueryWrapper<ScmDeliveryNoteLine>().eq(ScmDeliveryNoteLine::getDeliveryId, deliveryId));
        if (allLines == null || allLines.isEmpty()) throw new ServiceException("到货单明细为空");
        Map<Long, ScmDeliveryNoteLine> lineMap = new HashMap<>();
        for (ScmDeliveryNoteLine l : allLines) lineMap.put(l.getLineId(), l);

        // PO行->单价 缓存（用于退货草稿行取 unitPrice）
        Map<Long, BigDecimal> poLineUnitPrice = new HashMap<>();
        for (ScmDeliveryNoteLine l : allLines) {
            if (l.getPoLineId() != null) {
                ScmPurchaseOrderLine pol = poLineMapper.selectById(l.getPoLineId());
                if (pol != null) poLineUnitPrice.put(l.getLineId(), nvl(pol.getUnitPrice()));
            }
        }

        // -------- 1. 若PO行无需质检（qc_required=0 的直通）则跳过细节校验直接返回"直通" --------
        // 此处不短路整单：逐行判定 judgements 的 lineId 是否属于 deliveryId，并按该 delivery_line.qcRequired 处理
        // 结果收集
        int linesUpdated = 0;
        BigDecimal totalStockedQty = BigDecimal.ZERO;
        BigDecimal totalScrappedQty = BigDecimal.ZERO;
        List<Map<String,Object>> returnsGenerated = new ArrayList<>();
        // 拒收分组（按 reasonCode 分组生成退货单，便于供应商看一张单对应一种原因）
        Map<String, List<ScmDeliveryNoteLine>> rejectByReason = new LinkedHashMap<>();
        Map<String, List<Map<String,Object>>> rejectJudgementByReason = new LinkedHashMap<>();

        // -------- 2. 逐行校验 & 更新 delivery_line --------
        for (Map<String,Object> j : judgements) {
            Long lineId = toLong(j.get("lineId"));
            if (lineId == null) throw new ServiceException("judgement缺少lineId");
            ScmDeliveryNoteLine dl = lineMap.get(lineId);
            if (dl == null) throw new ServiceException("lineId=" + lineId + " 不属于当前到货单");

            // qc_required=0 的直通行：如果 judgements 也对它提报，则直接标记为"合格"并跳过库存（尚未入WH-IQC，按直通处理）
            Integer qcReq = resolveQcRequired(dl, delivery);
            if (qcReq != null && qcReq == 0) {
                dl.setQcStatus(2);  // 合格
                dl.setQcDisposition("ACCEPT");
                dl.setQcAcceptedQty(nvl(dl.getQcAcceptedQty()).add(nvl(dl.getDeliveryQty())));
                dl.setQcInspectedQty(nvl(dl.getQcInspectedQty()).add(nvl(dl.getDeliveryQty())));
                dl.setQcInspectTime(LocalDateTime.now());
                String insp = j.get("inspector") == null ? null : j.get("inspector").toString();
                if (insp != null) dl.setQcInspector(insp);
                if (j.get("remark") != null) dl.setQcRemark(j.get("remark").toString());
                lineMapper.updateById(dl);
                linesUpdated++;
                continue;
            }

            BigDecimal inspected = toDecimal(j.get("inspectedQty"));
            BigDecimal accepted = toDecimal(j.get("acceptedQty"));
            BigDecimal concession = toDecimal(j.get("concessionQty"));
            BigDecimal rejected = toDecimal(j.get("rejectedQty"));
            BigDecimal scrapped = toDecimal(j.get("scrappedQty"));
            String disposition = j.get("disposition") == null ? "" : j.get("disposition").toString().trim().toUpperCase();
            String reasonCode = j.get("reasonCode") == null ? null : j.get("reasonCode").toString();
            String remark = j.get("remark") == null ? null : j.get("remark").toString();
            String inspector = j.get("inspector") == null ? null : j.get("inspector").toString();

            // 校验 2a: 数量守恒
            if (inspected.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal sum = accepted.add(concession).add(rejected).add(scrapped);
                if (sum.compareTo(inspected) != 0) {
                    throw new ServiceException("qc判定各去向数量之和需等于检验数量，lineId=" + lineId);
                }
            }
            // 校验 2b: 拒收必填原因码
            if (rejected.compareTo(BigDecimal.ZERO) > 0 && (reasonCode == null || reasonCode.isBlank())) {
                throw new ServiceException("拒收必须提供缺陷原因码，lineId=" + lineId);
            }

            // 更新 delivery_line 字段
            dl.setQcInspectedQty(nvl(dl.getQcInspectedQty()).add(inspected));
            dl.setQcAcceptedQty(nvl(dl.getQcAcceptedQty()).add(accepted));
            dl.setQcConcessionQty(nvl(dl.getQcConcessionQty()).add(concession));
            dl.setQcRejectedQty(nvl(dl.getQcRejectedQty()).add(rejected));
            dl.setQcScrappedQty(nvl(dl.getQcScrappedQty()).add(scrapped));
            // 兼容旧字段 acceptedQty / concessionQty / rejectedQty
            dl.setAcceptedQty(nvl(dl.getAcceptedQty()).add(accepted));
            dl.setConcessionQty(nvl(dl.getConcessionQty()).add(concession));
            dl.setRejectedQty(nvl(dl.getRejectedQty()).add(rejected));

            if (disposition.isEmpty()) disposition = inferDisposition(accepted, concession, rejected, scrapped);
            dl.setQcDisposition(disposition);
            int lineQcStatus = resolveLineQcStatus(disposition);
            dl.setQcStatus(lineQcStatus);
            if (reasonCode != null) dl.setQcReasonCode(reasonCode);
            if (remark != null) dl.setQcRemark(remark);
            if (inspector != null) dl.setQcInspector(inspector);
            dl.setQcInspectTime(LocalDateTime.now());
            if (dl.getStockedQty() == null) dl.setStockedQty(BigDecimal.ZERO);
            lineMapper.updateById(dl);
            linesUpdated++;

            // 归集：调拨 accepted+concession（后续统一处理仓库）
            // 归集：报废
            totalScrappedQty = totalScrappedQty.add(scrapped);
            // 归集：退货分组
            if (rejected.compareTo(BigDecimal.ZERO) > 0) {
                String key = reasonCode == null ? "UNKNOWN" : reasonCode;
                rejectByReason.computeIfAbsent(key, k -> new ArrayList<>()).add(dl);
                rejectJudgementByReason.computeIfAbsent(key, k -> new ArrayList<>()).add(j);
            }
        }

        // -------- 3. 更新 delivery 头 qc_status --------
        // 1=待检 2=部分已判 3=全部已判
        // 重新从 DB 拉最新
        List<ScmDeliveryNoteLine> freshLines = lineMapper.selectList(
                new LambdaQueryWrapper<ScmDeliveryNoteLine>().eq(ScmDeliveryNoteLine::getDeliveryId, deliveryId));
        int judged = 0;
        for (ScmDeliveryNoteLine l : freshLines) {
            if (l.getQcStatus() != null && l.getQcStatus() != 0) judged++;
        }
        int headQcStatus;
        if (judged == 0) headQcStatus = 1;
        else if (judged == freshLines.size()) headQcStatus = 3;
        else headQcStatus = 2;
        delivery.setQcStatus(headQcStatus);
        // 头 inspector 取第一条非空 inspector
        String headerInspector = null;
        for (ScmDeliveryNoteLine l : freshLines) {
            if (l.getQcInspector() != null && !l.getQcInspector().isBlank()) {
                headerInspector = l.getQcInspector();
                break;
            }
        }
        if (headerInspector != null) delivery.setQcInspector(headerInspector);
        mapper.updateById(delivery);

        // -------- 4. 仓库查询：WH-IQC + targetRaw --------
        Long iqcWarehouseId = resolveIqcWarehouseId();
        if (iqcWarehouseId == null) {
            throw new ServiceException("系统未配置 WH-IQC 质检仓，请在 wms_warehouse 先创建编码为 WH-IQC 的仓库");
        }
        Long targetRawWarehouseId = resolveTargetRawWarehouseId(delivery);

        // -------- 5. 调拨 WH-IQC → targetRaw：acceptedQty + concessionQty --------
        // 统一按到货单维度聚合每个物料(+批次)的调拨数量，避免逐行调用导致的重复库存操作
        Map<String, TransferMeta> transferMap = new LinkedHashMap<>();
        for (ScmDeliveryNoteLine dl : freshLines) {
            BigDecimal a = nvl(dl.getQcAcceptedQty());
            BigDecimal c = nvl(dl.getQcConcessionQty());
            BigDecimal toMove = a.add(c);
            // 已 stockedQty 要扣掉
            BigDecimal alreadyStocked = nvl(dl.getStockedQty());
            BigDecimal remain = toMove.subtract(alreadyStocked);
            if (remain.compareTo(BigDecimal.ZERO) <= 0) continue;
            String key = (dl.getMaterialId() == null ? "?" : dl.getMaterialId()) + "|" + (dl.getBatchNo() == null ? "" : dl.getBatchNo());
            TransferMeta tm = transferMap.computeIfAbsent(key, k -> new TransferMeta(dl.getMaterialId(), dl.getBatchNo(), dl.getUnit()));
            tm.qty = tm.qty.add(remain);
            tm.deliveryLines.add(dl);
        }

        // 生成调拨记录（走 WMS：先扣 WH-IQC 再加 targetRaw；同时生成一张 TRANSFER 类型的入库单留痕并直接确认入库）
        if (!transferMap.isEmpty()) {
            WmsStockIn transferStockIn = new WmsStockIn();
            transferStockIn.setWarehouseId(targetRawWarehouseId);
            transferStockIn.setInType("TRANSFER");
            transferStockIn.setSupplierId(delivery.getSupplierId());
            transferStockIn.setSupplierName(delivery.getSupplierName());
            transferStockIn.setRemark("IQC合格/让步调拨：WH-IQC→实仓 来源到货单:" + (delivery.getDeliveryNo() == null ? deliveryId : delivery.getDeliveryNo()));
            List<WmsStockInLine> tLines = new ArrayList<>();
            for (TransferMeta tm : transferMap.values()) {
                // 扣 WH-IQC
                if (tm.qty.compareTo(BigDecimal.ZERO) > 0) {
                    int r = inventoryService.decreaseInventory(iqcWarehouseId, tm.materialId, tm.batchNo, tm.qty);
                    if (r <= 0) {
                        throw new ServiceException("WH-IQC 库存不足，无法调拨。物料:" + tm.materialId + " 批次:" + tm.batchNo + " 需扣:" + tm.qty);
                    }
                    // 加实仓
                    inventoryService.increaseInventory(targetRawWarehouseId, tm.materialId, tm.batchNo, tm.qty, tm.unit);
                }
                // 生成调拨入库单行（聚合）
                for (ScmDeliveryNoteLine dl : tm.deliveryLines) {
                    BigDecimal a = nvl(dl.getQcAcceptedQty());
                    BigDecimal c = nvl(dl.getQcConcessionQty());
                    BigDecimal toMove = a.add(c).subtract(nvl(dl.getStockedQty()));
                    if (toMove.compareTo(BigDecimal.ZERO) <= 0) continue;
                    WmsStockInLine sl = new WmsStockInLine();
                    sl.setDeliveryId(deliveryId);
                    sl.setDeliveryLineId(dl.getLineId());
                    sl.setPoLineId(dl.getPoLineId());
                    sl.setPoId(delivery.getPoId());
                    sl.setMaterialId(dl.getMaterialId());
                    sl.setMaterialCode(dl.getMaterialCode());
                    sl.setMaterialName(dl.getMaterialName());
                    sl.setSpecification(dl.getSpecification());
                    sl.setUnit(dl.getUnit());
                    sl.setBatchNo(dl.getBatchNo());
                    sl.setQuantity(toMove);
                    // qc_flag=concession 让步放行则标记 1
                    int qcFlag = (c.compareTo(BigDecimal.ZERO) > 0) ? 1 : 0;
                    sl.setRemark("IQC调拨入实仓 qcFlag=" + qcFlag + (dl.getQcRemark() == null ? "" : " " + dl.getQcRemark()));
                    tLines.add(sl);
                    // 回写 stockedQty
                    dl.setStockedQty(nvl(dl.getStockedQty()).add(toMove));
                    totalStockedQty = totalStockedQty.add(toMove);
                    lineMapper.updateById(dl);
                }
            }
            transferStockIn.setLines(tLines);
            Long tStockInId = stockInService.saveStockIn(transferStockIn);
            // 自动审核 + 确认入库（留痕且不阻塞用户）
            if (tStockInId != null) {
                try {
                    String by = headerInspector == null ? "system" : headerInspector;
                    stockInService.auditPass(tStockInId, by);
                    stockInService.confirmStockIn(tStockInId);
                } catch (Exception e) {
                    log.warn("[qcJudge] 调拨入库单自动确认失败 stockInId={} msg={}", tStockInId, e.getMessage());
                }
            }
        }

        // -------- 6. 拒收 rejectedQty>0 → 生成 scm_purchase_return 草稿(status=0, qc_flag=1) --------
        for (Map.Entry<String, List<ScmDeliveryNoteLine>> entry : rejectByReason.entrySet()) {
            String reasonCode = entry.getKey();
            List<ScmDeliveryNoteLine> rejectLines = entry.getValue();
            ScmPurchaseReturn ret = new ScmPurchaseReturn();
            ret.setPoId(delivery.getPoId());
            ret.setDeliveryId(delivery.getDeliveryId());
            ret.setSupplierId(delivery.getSupplierId());
            ret.setSupplierName(delivery.getSupplierName());
            ret.setReturnDate(LocalDate.now());
            ret.setWarehouseId(targetRawWarehouseId);
            ret.setReturnReason("IQC拒收：" + reasonCode);
            ret.setHasRefund(0);
            ret.setRefundAmount(BigDecimal.ZERO);
            ret.setStatus(0); // 草稿
            Long returnId = purchaseReturnService.save(ret);
            if (returnId == null) throw new ServiceException("生成退货单失败，原因码:" + reasonCode);
            int retLineNo = 0;
            BigDecimal retTotalQty = BigDecimal.ZERO;
            BigDecimal retTotalAmt = BigDecimal.ZERO;
            for (ScmDeliveryNoteLine dl : rejectLines) {
                BigDecimal thisRejected = nvl(dl.getQcRejectedQty());
                if (thisRejected.compareTo(BigDecimal.ZERO) <= 0) continue;
                retLineNo++;
                BigDecimal unitPrice = poLineUnitPrice.getOrDefault(dl.getLineId(), BigDecimal.ZERO);
                ScmPurchaseReturnLine rl = new ScmPurchaseReturnLine();
                rl.setReturnId(returnId);
                rl.setLineNo(retLineNo);
                rl.setDeliveryLineId(dl.getLineId());
                rl.setMaterialId(dl.getMaterialId());
                rl.setMaterialCode(dl.getMaterialCode());
                rl.setMaterialName(dl.getMaterialName());
                rl.setSpecification(dl.getSpecification());
                rl.setUnit(dl.getUnit());
                rl.setReturnQty(thisRejected);
                rl.setUnitPrice(unitPrice);
                rl.setReturnAmount(thisRejected.multiply(unitPrice));
                rl.setBatchNo(dl.getBatchNo());
                rl.setQcFlag(1);
                rl.setDefectCode(reasonCode);
                rl.setQcReasonCode(reasonCode);
                rl.setDefectDesc(dl.getQcRemark());
                // remark: 把 inspector/remark 透传
                rl.setRemark("IQC拒收 inspector=" + (dl.getQcInspector() == null ? "" : dl.getQcInspector()) + (dl.getRemark() == null ? "" : " " + dl.getRemark()));
                returnLineMapper.insert(rl);
                retTotalQty = retTotalQty.add(thisRejected);
                retTotalAmt = retTotalAmt.add(rl.getReturnAmount());
            }
            // 回写退货单汇总
            ret.setTotalQty(retTotalQty);
            ret.setTotalAmount(retTotalAmt);
            try {
                Field rf = ScmPurchaseReturn.class.getDeclaredField("qcFlag");
            } catch (NoSuchFieldException ignore) {
                // ScmPurchaseReturn 没有 qcFlag 字段（放头的非必需），放行
            }
            ScmPurchaseReturn forUpd = new ScmPurchaseReturn();
            forUpd.setReturnId(returnId);
            forUpd.setTotalQty(retTotalQty);
            forUpd.setTotalAmount(retTotalAmt);
            purchaseReturnService.update(forUpd);
            // 汇总返回
            Map<String,Object> r = new HashMap<>();
            r.put("returnId", returnId);
            r.put("totalQty", retTotalQty);
            returnsGenerated.add(r);
        }

        // -------- 7. 报废 scrappedQty>0：直接扣 WH-IQC 库存 --------
        if (totalScrappedQty.compareTo(BigDecimal.ZERO) > 0) {
            // 按 line 维度聚合 scrap（逐行扣，避免批次混淆）
            for (Map<String,Object> j : judgements) {
                Long lineId = toLong(j.get("lineId"));
                if (lineId == null) continue;
                ScmDeliveryNoteLine dl = lineMap.get(lineId);
                if (dl == null) continue;
                BigDecimal scrapQty = toDecimal(j.get("scrappedQty"));
                if (scrapQty.compareTo(BigDecimal.ZERO) <= 0) continue;
                int r = inventoryService.decreaseInventory(iqcWarehouseId, dl.getMaterialId(), dl.getBatchNo(), scrapQty);
                if (r <= 0) {
                    throw new ServiceException("WH-IQC 报废扣减失败：物料" + dl.getMaterialId() + " 批次" + dl.getBatchNo() + " 不足" + scrapQty);
                }
                // delivery_line.qcScrappedQty 已在前面累加过（在更新 delivery_line 处）
            }
        }

        // -------- 8. 组装返回结果 --------
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("deliveryId", deliveryId);
        result.put("qcStatus", headQcStatus);
        result.put("linesUpdated", linesUpdated);
        result.put("stockedQty", totalStockedQty);
        result.put("returnsGenerated", returnsGenerated);
        result.put("scrappedQty", totalScrappedQty);
        return result;
    }

    /* ================== 私有辅助方法 ================== */

    private static class TransferMeta {
        Long materialId;
        String batchNo;
        String unit;
        BigDecimal qty = BigDecimal.ZERO;
        List<ScmDeliveryNoteLine> deliveryLines = new ArrayList<>();
        TransferMeta(Long materialId, String batchNo, String unit) {
            this.materialId = materialId;
            this.batchNo = batchNo;
            this.unit = unit;
        }
    }

    /** 解析 PO行 qcRequired → 若 PO 行无法解析则退回 line 自身已有字段 */
    private Integer resolveQcRequired(ScmDeliveryNoteLine dl, ScmDeliveryNote head) {
        if (dl.getPoLineId() != null) {
            ScmPurchaseOrderLine pol = poLineMapper.selectById(dl.getPoLineId());
            if (pol != null && pol.getQcRequired() != null) return pol.getQcRequired();
        }
        // 头级控制
        return head.getQcRequired();
    }

    /** 根据各去向数量推断 disposition */
    private String inferDisposition(BigDecimal a, BigDecimal c, BigDecimal r, BigDecimal s) {
        int nonZero = 0;
        if (a.compareTo(BigDecimal.ZERO) > 0) nonZero++;
        if (c.compareTo(BigDecimal.ZERO) > 0) nonZero++;
        if (r.compareTo(BigDecimal.ZERO) > 0) nonZero++;
        if (s.compareTo(BigDecimal.ZERO) > 0) nonZero++;
        if (nonZero > 1) return "MIXED";
        if (s.compareTo(BigDecimal.ZERO) > 0) return "SCRAP";
        if (r.compareTo(BigDecimal.ZERO) > 0) return "REJECT";
        if (c.compareTo(BigDecimal.ZERO) > 0) return "CONCESSION";
        return "ACCEPT";
    }

    /** disposition → 行 qcStatus：2=合格 3=让步 4=拒收/混合 5=报废 */
    private int resolveLineQcStatus(String disposition) {
        if (disposition == null) return 0;
        switch (disposition) {
            case "ACCEPT": return 2;
            case "CONCESSION": return 3;
            case "REJECT":
            case "MIXED": return 4;
            case "SCRAP": return 5;
            default: return 0;
        }
    }

    /** 解析 WH-IQC 仓库ID */
    private Long resolveIqcWarehouseId() {
        try {
            List<WmsWarehouse> list = warehouseMapper.selectList(
                    new LambdaQueryWrapper<WmsWarehouse>().eq(WmsWarehouse::getWarehouseCode, "WH-IQC").last("LIMIT 1"));
            if (list != null && !list.isEmpty()) return list.get(0).getWarehouseId();
        } catch (Exception e) {
            log.warn("[resolveIqcWarehouseId] mapper查询失败: {}", e.getMessage());
        }
        // 兜底：JDBC 直接查
        try {
            Long id = jdbcTemplate.queryForObject(
                    "SELECT warehouse_id FROM wms_warehouse WHERE warehouse_code='WH-IQC' LIMIT 1", Long.class);
            if (id != null) return id;
        } catch (Exception ignore) {}
        return null;
    }

    /** 解析目标原材料仓ID：默认RAW类型非虚拟仓 → 历史 WmsStockIn → fallback 1L */
    private Long resolveTargetRawWarehouseId(ScmDeliveryNote delivery) {
        // 1) delivery 头已指定仓库（非默认1），非虚拟仓就直接用
        if (delivery.getWarehouseId() != null && delivery.getWarehouseId() != 1L) {
            WmsWarehouse w = warehouseMapper.selectById(delivery.getWarehouseId());
            if (w != null && w.getStatus() != null && w.getStatus() == 0) {
                return delivery.getWarehouseId();
            }
        }
        // 2) PO 行默认原材料仓：warehouse_type=RAW，非虚拟仓(warehouse_type!=99)，status=0
        try {
            List<WmsWarehouse> raws = warehouseMapper.selectList(
                    new LambdaQueryWrapper<WmsWarehouse>()
                            .eq(WmsWarehouse::getStatus, 0)
                            .ne(WmsWarehouse::getWarehouseType, "99")
                            .and(w -> w.eq(WmsWarehouse::getWarehouseType, "RAW").or().likeRight(WmsWarehouse::getWarehouseCode, "WH-0"))
                            .orderByAsc(WmsWarehouse::getWarehouseId).last("LIMIT 1"));
            if (raws != null && !raws.isEmpty()) return raws.get(0).getWarehouseId();
        } catch (Exception e) {
            log.warn("[resolveTargetRawWarehouseId] RAW查询异常: {}", e.getMessage());
        }
        // 3) JDBC: 按 warehouse_type != 99 取第一个 status=0
        try {
            Long id = jdbcTemplate.queryForObject(
                    "SELECT w.warehouse_id FROM wms_warehouse w WHERE w.warehouse_type != 99 AND w.status=0 ORDER BY w.warehouse_id LIMIT 1", Long.class);
            if (id != null) return id;
        } catch (Exception ignore) {}
        // 4) 从 WmsStockIn 历史找第一个非虚拟仓
        try {
            Long id = jdbcTemplate.queryForObject(
                    "SELECT DISTINCT s.warehouse_id FROM wms_stock_in s " +
                            "WHERE s.warehouse_id IS NOT NULL AND s.status=3 " +
                            "AND s.warehouse_id NOT IN (SELECT warehouse_id FROM wms_warehouse WHERE warehouse_type=99) " +
                            "ORDER BY s.stock_in_id DESC LIMIT 1", Long.class);
            if (id != null) return id;
        } catch (Exception ignore) {}
        // 5) fallback
        return 1L;
    }

    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Long) return (Long) o;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString().trim()); } catch (Exception e) { return null; }
    }
    private static BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return new BigDecimal(o.toString());
        try { return new BigDecimal(o.toString().trim()); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}
