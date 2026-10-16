package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.common.event.NoticeEvent;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.FasOrder;
import com.pcba.erp.pmm.entity.FasProductStock;
import com.pcba.erp.pmm.mapper.FasOrderMapper;
import com.pcba.erp.pmm.mapper.FasProductStockMapper;
import com.pcba.erp.pmm.service.IPmmFasStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class FasStockServiceImpl implements IPmmFasStockService {

    private final FasProductStockMapper mapper;
    private final FasOrderMapper orderMapper;
    private final ApplicationEventPublisher eventPublisher;

    /* ======================= 台账 (原逻辑保留, 新增 stockType 维度实现 FG/ACC 软隔离) ======================= */
    @Override
    public IPage<FasProductStock> selectPage(int pageNum, int pageSize, String productName, String batchNo, String warehouse) {
        // 旧接口保持无 stockType 过滤 → 兼容 admin 直查
        return mapper.selectPage(new Page<>(pageNum, pageSize), null, productName, batchNo, warehouse);
    }

    @Override
    public IPage<FasProductStock> selectPageByScope(int pageNum, int pageSize, String stockType,
                                                    String productName, String batchNo, String warehouse) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), stockType, productName, batchNo, warehouse);
    }

    @Override
    public FasProductStock getById(Long id) {
        if (id == null) throw new ServiceException("库存ID不能为空");
        FasProductStock stock = mapper.selectById(id);
        if (stock == null) throw new ServiceException("库存记录不存在");
        return stock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stockOut(Long stockId, Integer qty) {
        if (qty == null || qty <= 0) throw new ServiceException("出库数量必须大于0");
        FasProductStock stock = mapper.selectForUpdate(stockId);
        if (stock == null) throw new ServiceException("库存记录不存在");
        int available = stock.getQtyAvailable() == null ? 0 : stock.getQtyAvailable();
        if (qty > available) {
            throw new ServiceException("出库数量不能大于可用库存(" + available + ")");
        }
        int out = stock.getQtyOut() == null ? 0 : stock.getQtyOut();
        stock.setQtyOut(out + qty);
        stock.setQtyAvailable(available - qty);
        mapper.updateById(stock);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        FasProductStock stock = getById(id);
        Integer available = stock.getQtyAvailable();
        if (available != null && available > 0) {
            throw new ServiceException("存在可用库存, 禁止删除");
        }
        mapper.deleteById(id);
    }

    /* ======================= 完工入库单 ======================= */
    @Override
    public IPage<FasProductStock> selectInboundPage(int pageNum, int pageSize, Integer docStatus, String docNo,
                                                     String productName, String batchNo, String warehouse) {
        return mapper.selectDocPage(new Page<>(pageNum, pageSize),
                "INBOUND", docStatus, docNo, productName, batchNo, warehouse);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInboundDraftFromOrder(Long orderId, String warehouse, String batchNo, Integer qty, String remark) {
        if (orderId == null) throw new ServiceException("工单ID不能为空");
        FasOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new ServiceException("工单不存在");
        if (order.getStatus() == null || order.getStatus() != 3) {
            throw new ServiceException("只有已完工状态的工单才能生成入库单");
        }
        if (warehouse == null || warehouse.isBlank()) throw new ServiceException("入库仓库不能为空");
        if (qty == null || qty <= 0) throw new ServiceException("入库数量必须大于0");
        int completeQty = order.getQtyComplete() == null ? 0 : order.getQtyComplete();
        if (qty > completeQty) {
            throw new ServiceException("入库数量不能超过工单完工数量(" + completeQty + ")");
        }

        FasProductStock stock = new FasProductStock();
        stock.setProductId(order.getProductId());
        stock.setProductName(order.getProductName());
        stock.setBatchNo(batchNo);
        stock.setWarehouse(warehouse);
        stock.setQtyIn(0);               // 审核通过后再写入库数量
        stock.setQtyOut(0);
        stock.setQtyAvailable(0);
        stock.setOrderId(order.getOrderId());
        stock.setInTime(null);
        stock.setRemark(remark);
        stock.setDocType("INBOUND");
        stock.setDocNo(nextInboundDocNo());
        stock.setDocStatus(0);            // 草稿
        stock.setFlowQty(qty);            // 待入库数量
        stock.setSourceType("ASSEMBLY_IN");
        stock.setStockType("FG");         // 后道组装工单完工 → 成品整机
        mapper.insert(stock);

        // 工单状态更新为"待入库审核"(4)
        order.setStatus(4);
        orderMapper.updateById(order);

        eventPublisher.publishEvent(NoticeEvent.builder()
                .noticeType(2).noticeLevel(2)
                .title("完工入库单 " + stock.getDocNo() + " 待审核")
                .summary(order.getProductName() + " 待入库 " + qty + " PCS, 仓库: " + warehouse)
                .content("<p>后道组装工单 <b>" + order.getOrderNo() + "</b> 已生成完工入库单 <b>"
                        + stock.getDocNo() + "</b>，请仓管人员及时审核。</p>")
                .relatedType("FAS_STOCK_IN").relatedId(String.valueOf(stock.getStockId()))
                .actionRoute("/fas/finished-inbound").build());

        return stock.getStockId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitAudit(Long stockId) {
        FasProductStock s = requireDocStatus(stockId, 0);
        s.setDocStatus(1);
        mapper.updateById(s);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditPass(Long stockId) {
        FasProductStock s = requireDocStatus(stockId, 1);
        if (!"INBOUND".equals(s.getDocType())) throw new ServiceException("仅入库单可调用此审核");
        int qty = s.getFlowQty() == null ? 0 : s.getFlowQty();
        if (qty <= 0) throw new ServiceException("入库数量必须大于0");

        s.setDocStatus(3); // 跳过2(已审核)直接3(已完成/已入库)
        s.setAuditBy(SecurityUtils.getNickName());
        s.setAuditTime(LocalDateTime.now());
        s.setQtyIn(qty);
        s.setQtyAvailable(qty);
        s.setInTime(LocalDateTime.now());
        mapper.updateById(s);

        // 关联工单 状态改为5(已入库/完成)
        if (s.getOrderId() != null) {
            FasOrder order = orderMapper.selectById(s.getOrderId());
            if (order != null && order.getStatus() == 4) {
                order.setStatus(5);
                orderMapper.updateById(order);
            }
        }

        eventPublisher.publishEvent(NoticeEvent.builder()
                .noticeType(2).noticeLevel(1)
                .title("完工入库单 " + s.getDocNo() + " 已审核通过")
                .summary((s.getProductName() != null ? s.getProductName() : "-") + " 入库 " + qty + " PCS")
                .content("<p>完工入库单 <b>" + s.getDocNo() + "</b> 已审核通过并入库，入库数量 " + qty + " PCS，仓库：" + s.getWarehouse() + "。</p>")
                .relatedType("FAS_STOCK_IN").relatedId(String.valueOf(stockId))
                .actionRoute("/fas/stock").build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditReject(Long stockId, String rejectReason) {
        FasProductStock s = requireDocStatus(stockId, 1);
        if (rejectReason == null || rejectReason.isBlank()) throw new ServiceException("驳回原因不能为空");
        s.setDocStatus(4);
        s.setAuditBy(SecurityUtils.getNickName());
        s.setAuditTime(LocalDateTime.now());
        s.setRejectReason(rejectReason);
        mapper.updateById(s);

        // 关联工单回滚到 已完工(3)
        if ("INBOUND".equals(s.getDocType()) && s.getOrderId() != null) {
            FasOrder order = orderMapper.selectById(s.getOrderId());
            if (order != null && order.getStatus() == 4) {
                order.setStatus(3);
                orderMapper.updateById(order);
            }
        }

        eventPublisher.publishEvent(NoticeEvent.builder()
                .noticeType(2).noticeLevel(3)
                .title("入库/出库单 " + s.getDocNo() + " 被驳回")
                .summary("驳回原因: " + rejectReason)
                .content("<p>单据 <b>" + s.getDocNo() + "</b> 审核被驳回，原因：" + rejectReason + "，请重新编辑后提交。</p>")
                .relatedType("FAS_STOCK_IN").relatedId(String.valueOf(stockId))
                .actionRoute("/fas/finished-inbound").build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDoc(Long stockId) {
        FasProductStock s = getById(stockId);
        Integer st = s.getDocStatus();
        if (st == null || (st != 0 && st != 4)) {
            throw new ServiceException("仅草稿或驳回状态的单据可删除");
        }
        // 工单回滚 状态3
        if ("INBOUND".equals(s.getDocType()) && s.getOrderId() != null && Integer.valueOf(4).equals(st)) {
            FasOrder order = orderMapper.selectById(s.getOrderId());
            if (order != null && order.getStatus() == 4) {
                order.setStatus(3);
                orderMapper.updateById(order);
            }
        }
        mapper.deleteById(stockId);
    }

    /* ======================= 成品出库单 ======================= */
    @Override
    public IPage<FasProductStock> selectOutboundPage(int pageNum, int pageSize, Integer docStatus, String docNo,
                                                      String productName, String batchNo, String warehouse) {
        return mapper.selectDocPage(new Page<>(pageNum, pageSize),
                "OUTBOUND", docStatus, docNo, productName, batchNo, warehouse);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOutboundDraft(FasProductStock stock) {
        if (stock.getProductId() == null) throw new ServiceException("出库产品不能为空");
        if (stock.getWarehouse() == null || stock.getWarehouse().isBlank()) throw new ServiceException("出库仓库不能为空");
        int qty = stock.getFlowQty() == null ? 0 : stock.getFlowQty();
        if (qty <= 0) throw new ServiceException("出库数量必须大于0");
        stock.setFlowQty(-qty);  // 出库为负数
        stock.setDocType("OUTBOUND");
        stock.setDocNo(nextOutboundDocNo());
        stock.setDocStatus(0);
        if (stock.getSourceType() == null || stock.getSourceType().isBlank()) stock.setSourceType("OTHER");
        stock.setQtyIn(0);
        stock.setQtyOut(0);
        stock.setQtyAvailable(0);
        mapper.insert(stock);
        return stock.getStockId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void outboundAuditPass(Long stockId) {
        FasProductStock s = requireDocStatus(stockId, 1);
        if (!"OUTBOUND".equals(s.getDocType())) throw new ServiceException("仅出库单可调用此审核");
        int outAbs = Math.abs(s.getFlowQty() == null ? 0 : s.getFlowQty());
        if (outAbs <= 0) throw new ServiceException("出库数量必须大于0");

        // 校验当前台账可用库存 (产品+仓库 汇总)
        var list = mapper.selectAvailableForUpdate(s.getProductId(), s.getWarehouse(),
                s.getStockType() == null ? "FG" : s.getStockType(), s.getBatchNo());
        Integer available = list.stream()
                .mapToInt(r -> r.getQtyAvailable() == null ? 0 : r.getQtyAvailable()).sum();
        if (outAbs > available) {
            throw new ServiceException("库存不足, 当前可用 " + available + " PCS");
        }

        s.setDocStatus(3);
        s.setAuditBy(SecurityUtils.getNickName());
        s.setAuditTime(LocalDateTime.now());
        // 以单据自身承载出库扣减记录 (也可独立行扣减, 此处轻量实现)
        s.setQtyOut(outAbs);
        mapper.updateById(s);

        // 在原台账中扣减 qtyAvailable (选第一条可扣减的入库记录优先扣)
        int remain = outAbs;
        for (FasProductStock r : list) {
            if (remain <= 0) break;
            if (r.getStockId().equals(s.getStockId())) continue;
            if (!r.getProductId().equals(s.getProductId())) continue;
            int av = r.getQtyAvailable() == null ? 0 : r.getQtyAvailable();
            if (av <= 0) continue;
            int cut = Math.min(av, remain);
            r.setQtyAvailable(av - cut);
            int oldOut = r.getQtyOut() == null ? 0 : r.getQtyOut();
            r.setQtyOut(oldOut + cut);
            mapper.updateById(r);
            remain -= cut;
        }

        eventPublisher.publishEvent(NoticeEvent.builder()
                .noticeType(2).noticeLevel(2)
                .title("成品出库单 " + s.getDocNo() + " 已完成")
                .summary((s.getProductName() != null ? s.getProductName() : "-") + " 出库 " + outAbs + " PCS")
                .content("<p>成品出库单 <b>" + s.getDocNo() + "</b> 已审核通过并出库，出库数量 " + outAbs + " PCS，仓库：" + s.getWarehouse() + "。</p>")
                .relatedType("FAS_STOCK_OUT").relatedId(String.valueOf(stockId))
                .actionRoute("/fas/stock").build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void outboundAuditReject(Long stockId, String rejectReason) {
        auditReject(stockId, rejectReason);
    }

    /* ======================= 出入库流水 ======================= */
    @Override
    public IPage<FasProductStock> selectFlowPage(int pageNum, int pageSize, String docType, String sourceType,
                                                  String productName, String warehouse,
                                                  String beginTime, String endTime) {
        return mapper.selectFlowPage(new Page<>(pageNum, pageSize),
                docType, sourceType, productName, warehouse, beginTime, endTime);
    }

    /* ======================= 单号生成 ======================= */
    @Override
    public synchronized String nextInboundDocNo() {
        String prefix = "WFI" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String max = mapper.selectMaxDocNoByPrefix(prefix);
        int seq = 1;
        if (max != null && max.length() > prefix.length()) {
            try { seq = Integer.parseInt(max.substring(prefix.length())) + 1; } catch (NumberFormatException ignored) {}
        }
        return prefix + String.format("%03d", seq);
    }

    @Override
    public synchronized String nextOutboundDocNo() {
        String prefix = "WFO" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String max = mapper.selectMaxDocNoByPrefix(prefix);
        int seq = 1;
        if (max != null && max.length() > prefix.length()) {
            try { seq = Integer.parseInt(max.substring(prefix.length())) + 1; } catch (NumberFormatException ignored) {}
        }
        return prefix + String.format("%03d", seq);
    }

    /* ======================= 内部工具 ======================= */
    private FasProductStock requireDocStatus(Long id, int expected) {
        FasProductStock s = mapper.selectForUpdate(id);
        if (s == null) throw new ServiceException("单据不存在");
        Integer st = s.getDocStatus();
        if (st == null || st != expected) {
            String[] names = {"草稿", "待审核", "已审核", "已完成", "已驳回"};
            throw new ServiceException("单据状态错误，当前为【"
                    + (st != null && st < names.length ? names[st] : st) + "】，需要【" + names[expected] + "】");
        }
        return s;
    }
}
