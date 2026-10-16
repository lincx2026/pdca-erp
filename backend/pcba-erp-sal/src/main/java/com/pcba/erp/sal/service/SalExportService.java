package com.pcba.erp.sal.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcba.erp.sal.entity.SalArReceivable;
import com.pcba.erp.sal.entity.SalCustomer;
import com.pcba.erp.sal.entity.SalInvoice;
import com.pcba.erp.sal.entity.SalSalesOrder;
import com.pcba.erp.sal.entity.SalSalesReturn;
import com.pcba.erp.sal.mapper.SalArReceivableMapper;
import com.pcba.erp.sal.mapper.SalCustomerMapper;
import com.pcba.erp.sal.mapper.SalInvoiceMapper;
import com.pcba.erp.sal.mapper.SalSalesOrderMapper;
import com.pcba.erp.sal.mapper.SalSalesReturnMapper;
import com.pcba.erp.sal.vo.SalArReceivableExportVO;
import com.pcba.erp.sal.vo.SalInvoiceExportVO;
import com.pcba.erp.sal.vo.SalSalesOrderExportVO;
import com.pcba.erp.sal.vo.SalSalesReturnExportVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 销售模块 EasyExcel 导出（销售订单 / 销售退货 / 应收单）
 * - 复用 BaseMapper.selectList + LambdaQueryWrapper 按当前筛选条件导出（最多 10000 条）
 * - 状态/业务类型字段映射为中文文本，便于业务人员阅读
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalExportService {

    private static final int MAX_ROWS = 10000;

    private final SalSalesOrderMapper orderMapper;
    private final SalSalesReturnMapper returnMapper;
    private final SalArReceivableMapper receivableMapper;
    private final SalCustomerMapper customerMapper;
    private final SalInvoiceMapper invoiceMapper;

    /* ================ 销售订单导出 ================ */
    public void exportOrders(OutputStream out, String soNo, String soType, Long customerId,
                             String customerName, Long salesRepId, Integer status, String deliveryDate) {
        LambdaQueryWrapper<SalSalesOrder> w = new LambdaQueryWrapper<>();
        if (soNo != null && !soNo.isBlank()) w.like(SalSalesOrder::getSoNo, soNo);
        if (soType != null && !soType.isBlank()) w.eq(SalSalesOrder::getSoType, soType);
        if (customerId != null) w.eq(SalSalesOrder::getCustomerId, customerId);
        if (customerName != null && !customerName.isBlank()) w.like(SalSalesOrder::getCustomerName, customerName);
        if (salesRepId != null) w.eq(SalSalesOrder::getSalesRepId, salesRepId);
        if (status != null) w.eq(SalSalesOrder::getStatus, status);
        if (deliveryDate != null && !deliveryDate.isBlank()) w.like(SalSalesOrder::getDeliveryDate, deliveryDate);
        w.orderByDesc(SalSalesOrder::getSoId).last("LIMIT " + MAX_ROWS);

        List<SalSalesOrder> list = orderMapper.selectList(w);
        List<SalSalesOrderExportVO> rows = list.stream().map(this::toOrderVO).collect(Collectors.toList());
        EasyExcel.write(out, SalSalesOrderExportVO.class).sheet("销售订单").doWrite(rows);
        log.info("[exportOrders] 导出 {} 条", rows.size());
    }

    /* ================ 销售退货导出 ================ */
    public void exportReturns(OutputStream out, String returnNo, Long customerId,
                              String customerName, Long soId, Integer status, String returnDate) {
        LambdaQueryWrapper<SalSalesReturn> w = new LambdaQueryWrapper<>();
        if (returnNo != null && !returnNo.isBlank()) w.like(SalSalesReturn::getReturnNo, returnNo);
        if (customerId != null) w.eq(SalSalesReturn::getCustomerId, customerId);
        if (customerName != null && !customerName.isBlank()) w.like(SalSalesReturn::getCustomerName, customerName);
        if (soId != null) w.eq(SalSalesReturn::getSoId, soId);
        if (status != null) w.eq(SalSalesReturn::getStatus, status);
        if (returnDate != null && !returnDate.isBlank()) w.like(SalSalesReturn::getReturnDate, returnDate);
        w.orderByDesc(SalSalesReturn::getReturnId).last("LIMIT " + MAX_ROWS);

        List<SalSalesReturn> list = returnMapper.selectList(w);
        // 批量取关联销售订单编号 soId → soNo
        Set<Long> soIds = list.stream().map(SalSalesReturn::getSoId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> soNoMap = soIds.isEmpty() ? Collections.emptyMap() : orderMapper.selectBatchIds(soIds)
                .stream().collect(Collectors.toMap(SalSalesOrder::getSoId, SalSalesOrder::getSoNo, (a, b) -> a));

        List<SalSalesReturnExportVO> rows = list.stream()
                .map(r -> toReturnVO(r, soNoMap.getOrDefault(r.getSoId(), "")))
                .collect(Collectors.toList());
        EasyExcel.write(out, SalSalesReturnExportVO.class).sheet("销售退货").doWrite(rows);
        log.info("[exportReturns] 导出 {} 条", rows.size());
    }

    /* ================ 应收单导出 ================ */
    public void exportReceivables(OutputStream out, String receivableNo, Long customerId,
                                   String customerName, Integer status, String bizDate,
                                   String dueDate, Integer isOverdue) {
        LambdaQueryWrapper<SalArReceivable> w = new LambdaQueryWrapper<>();
        if (receivableNo != null && !receivableNo.isBlank()) w.like(SalArReceivable::getReceivableNo, receivableNo);
        if (customerId != null) w.eq(SalArReceivable::getCustomerId, customerId);
        if (customerName != null && !customerName.isBlank()) w.like(SalArReceivable::getCustomerName, customerName);
        if (status != null) w.eq(SalArReceivable::getStatus, status);
        if (bizDate != null && !bizDate.isBlank()) w.eq(SalArReceivable::getBizDate, bizDate);
        if (dueDate != null && !dueDate.isBlank()) w.eq(SalArReceivable::getDueDate, dueDate);
        if (isOverdue != null) w.eq(SalArReceivable::getIsOverdue, isOverdue);
        w.orderByDesc(SalArReceivable::getReceivableId).last("LIMIT " + MAX_ROWS);

        List<SalArReceivable> list = receivableMapper.selectList(w);
        List<SalArReceivableExportVO> rows = list.stream().map(this::toReceivableVO).collect(Collectors.toList());
        EasyExcel.write(out, SalArReceivableExportVO.class).sheet("应收单").doWrite(rows);
        log.info("[exportReceivables] 导出 {} 条", rows.size());
    }

    /* ================ 开票单导出 ================ */
    public void exportInvoices(OutputStream out, String invoiceNo, Long customerId,
                                String invoiceType, Integer status,
                                String dateFrom, String dateTo) {
        LambdaQueryWrapper<SalInvoice> w = new LambdaQueryWrapper<>();
        if (invoiceNo != null && !invoiceNo.isBlank()) w.like(SalInvoice::getInvoiceNo, invoiceNo);
        if (customerId != null) w.eq(SalInvoice::getCustomerId, customerId);
        if (invoiceType != null && !invoiceType.isBlank()) w.eq(SalInvoice::getInvoiceType, invoiceType);
        if (status != null) w.eq(SalInvoice::getStatus, status);
        try {
            if (dateFrom != null && !dateFrom.isBlank()) w.ge(SalInvoice::getInvoiceDate, LocalDate.parse(dateFrom));
            if (dateTo != null && !dateTo.isBlank()) w.le(SalInvoice::getInvoiceDate, LocalDate.parse(dateTo));
        } catch (Exception ignore) {}
        w.orderByDesc(SalInvoice::getInvoiceId).last("LIMIT " + MAX_ROWS);
        List<SalInvoice> list = invoiceMapper.selectList(w);
        List<SalInvoiceExportVO> rows = list.stream().map(this::toInvoiceVO).collect(Collectors.toList());
        EasyExcel.write(out, SalInvoiceExportVO.class).sheet("开票单").doWrite(rows);
        log.info("[exportInvoices] 导出 {} 条", rows.size());
    }

    private SalInvoiceExportVO toInvoiceVO(SalInvoice i) {
        SalInvoiceExportVO v = new SalInvoiceExportVO();
        v.setInvoiceNo(i.getInvoiceNo());
        v.setInvoiceType(invoiceTypeText(i.getInvoiceType()));
        v.setCustomerCode(i.getCustomerCode());
        v.setCustomerName(i.getCustomerName());
        v.setInvoiceDate(i.getInvoiceDate());
        v.setUndiscAmount(i.getUndiscAmount());
        v.setTaxAmount(i.getTaxAmount());
        v.setTotalAmount(i.getTotalAmount());
        v.setCnyTotalAmount(i.getCnyTotalAmount());
        v.setBuyerName(i.getBuyerName());
        v.setBuyerTaxNo(i.getBuyerTaxNo());
        v.setStatus(invoiceStatusText(i.getStatus()));
        v.setIssueBy(i.getIssueBy());
        v.setIssueTime(i.getIssueTime());
        v.setRemark(i.getRemark());
        return v;
    }
    private String invoiceTypeText(String t) {
        if (t == null) return "";
        switch (t) {
            case "1": return "专票";
            case "2": return "普票";
            case "3": return "电子专票";
            case "4": return "电子普票";
            default: return t;
        }
    }
    private String invoiceStatusText(Integer s) {
        if (s == null) return "";
        switch (s) {
            case 0: return "草稿";
            case 1: return "已开";
            case 2: return "作废";
            default: return String.valueOf(s);
        }
    }

    /* ================ 客户对账单导出 ================ */
    /**
     * 客户对账单 Excel：头部客户/周期信息 + 汇总行(期初/本期蓝字/本期红字/本期收款/期末) + 明细列表
     */
    public void exportArStatement(OutputStream out, Long customerId, String dateFrom, String dateTo) {
        if (customerId == null) throw new IllegalArgumentException("customerId 必填");
        if (dateFrom == null || dateTo == null) throw new IllegalArgumentException("dateFrom/dateTo 必填");
        LocalDate dFrom = LocalDate.parse(dateFrom);
        LocalDate dTo = LocalDate.parse(dateTo);

        SalCustomer c = customerMapper.selectById(customerId);
        String customerName = c == null ? "" : (c.getCustomerName() == null ? "" : c.getCustomerName());
        String customerCode = c == null ? "" : (c.getCustomerCode() == null ? "" : c.getCustomerCode());

        Map<String,Object> sum = receivableMapper.arStatementSummary(customerId, dFrom, dTo);
        BigDecimal beforeUnreceived = toBd(sum.get("beforeUnreceived"));
        BigDecimal occurredBlue = toBd(sum.get("occurredBlue"));
        BigDecimal occurredRed = toBd(sum.get("occurredRed"));
        BigDecimal receivedInPeriod = toBd(sum.get("receivedInPeriod"));
        BigDecimal endUnreceived = beforeUnreceived.add(occurredBlue).add(occurredRed).subtract(receivedInPeriod);
        if (endUnreceived.compareTo(BigDecimal.ZERO) < 0) endUnreceived = BigDecimal.ZERO;

        List<SalArReceivable> lines = receivableMapper.arStatementLines(customerId, dFrom, dTo);

        // 头部汇总块：每行一个键值对
        List<List<Object>> head = new ArrayList<>();
        head.add(Arrays.asList("客户对账单", customerCode, customerName, dateFrom, dateTo, "生成日期: " + LocalDate.now().toString()));
        // 汇总块（占 5 行）
        head.add(Arrays.asList("项 目", "金额(CNY)"));
        head.add(Arrays.asList("期初未收", beforeUnreceived));
        head.add(Arrays.asList("本期蓝字发生", occurredBlue));
        head.add(Arrays.asList("本期红字冲销", occurredRed));
        head.add(Arrays.asList("本期收款", receivedInPeriod));
        head.add(Arrays.asList("期末未收", endUnreceived));
        // 空行 + 明细表头
        head.add(Arrays.asList("", ""));
        head.add(Arrays.asList("应收单号","业务类型","业务日期","到期日","币种","单据金额","已收金额","未收金额","状态","对账人","备注"));
        // 明细行
        for (SalArReceivable r : lines) {
            head.add(Arrays.asList(
                    r.getReceivableNo(),
                    bizTypeText(r.getBizType()),
                    r.getBizDate() == null ? "" : r.getBizDate().toString(),
                    r.getDueDate() == null ? "" : r.getDueDate().toString(),
                    r.getCurrency() == null ? "" : r.getCurrency(),
                    r.getTotalAmount(),
                    r.getReceivedAmount(),
                    r.getUnreceivedAmount(),
                    arStatusText(r.getStatus()),
                    r.getReconcileBy() == null ? "" : r.getReconcileBy(),
                    r.getRemark() == null ? "" : r.getRemark()
            ));
        }

        // 用 ExcelWriter 写一个 sheet（无 @ExcelProperty 注解，使用 List<List<Object>> 二维表）
        ExcelWriter writer = EasyExcel.write(out).build();
        WriteSheet sheet = EasyExcel.writerSheet("对账单").build();
        writer.write(head, sheet);
        writer.finish();
        log.info("[exportArStatement] customerId={} range={}~{} 期初={} 期末={} 明细 {} 条",
                customerId, dateFrom, dateTo, beforeUnreceived, endUnreceived, lines.size());
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    /* ================ 映射 ================ */
    private SalSalesOrderExportVO toOrderVO(SalSalesOrder o) {
        SalSalesOrderExportVO v = new SalSalesOrderExportVO();
        v.setSoNo(o.getSoNo());
        v.setSoType(o.getSoType());
        v.setCustomerCode(o.getCustomerCode());
        v.setCustomerName(o.getCustomerName());
        v.setContactName(o.getContactName());
        v.setContactPhone(o.getContactPhone());
        v.setSalesRepName(o.getSalesRepName());
        v.setSoDate(o.getSoDate());
        v.setDeliveryDate(o.getDeliveryDate());
        v.setCurrency(o.getCurrency());
        v.setTaxRate(o.getTaxRate());
        v.setUndiscAmount(o.getUndiscAmount());
        v.setTaxAmount(o.getTaxAmount());
        v.setTotalAmount(o.getTotalAmount());
        v.setReceivedAmount(o.getReceivedAmount());
        v.setStatus(soStatusText(o.getStatus()));
        v.setAuditBy(o.getAuditBy());
        v.setCreateTime(o.getCreateTime());
        v.setRemark(o.getRemark());
        return v;
    }

    private SalSalesReturnExportVO toReturnVO(SalSalesReturn r, String soNo) {
        SalSalesReturnExportVO v = new SalSalesReturnExportVO();
        v.setReturnNo(r.getReturnNo());
        v.setCustomerCode(r.getCustomerCode());
        v.setCustomerName(r.getCustomerName());
        v.setSoNo(soNo);
        v.setReturnDate(r.getReturnDate());
        v.setCurrency(r.getCurrency());
        v.setTotalAmount(r.getTotalAmount());
        v.setStatus(returnStatusText(r.getStatus()));
        v.setStockInId(r.getStockInId() == null ? "" : r.getStockInId().toString());
        v.setCreateTime(r.getCreateTime());
        v.setRemark(r.getRemark());
        return v;
    }

    private SalArReceivableExportVO toReceivableVO(SalArReceivable a) {
        SalArReceivableExportVO v = new SalArReceivableExportVO();
        v.setReceivableNo(a.getReceivableNo());
        v.setBizType(bizTypeText(a.getBizType()));
        v.setCustomerCode(a.getCustomerCode());
        v.setCustomerName(a.getCustomerName());
        v.setCurrency(a.getCurrency());
        v.setUndiscAmount(a.getUndiscAmount());
        v.setTaxAmount(a.getTaxAmount());
        v.setTotalAmount(a.getTotalAmount());
        v.setCnyTotalAmount(a.getCnyTotalAmount());
        v.setReceivedAmount(a.getReceivedAmount());
        v.setUnreceivedAmount(a.getUnreceivedAmount());
        v.setBizDate(a.getBizDate());
        v.setDueDate(a.getDueDate());
        v.setIsOverdue(a.getIsOverdue() != null && a.getIsOverdue() == 1 ? "是" : "否");
        v.setOverdueDays(a.getOverdueDays());
        v.setStatus(arStatusText(a.getStatus()));
        v.setReconcileBy(a.getReconcileBy());
        v.setCreateTime(a.getCreateTime());
        v.setRemark(a.getRemark());
        return v;
    }

    private String soStatusText(Integer s) {
        if (s == null) return "";
        return switch (s) {
            case 0 -> "草稿"; case 1 -> "提交"; case 2 -> "已审核"; case 3 -> "已发货";
            case 4 -> "部分出库"; case 5 -> "全部出库"; case 6 -> "已完成"; case 7 -> "已驳回"; case 9 -> "取消";
            default -> String.valueOf(s);
        };
    }

    private String returnStatusText(Integer s) {
        if (s == null) return "";
        return switch (s) {
            case 0 -> "草稿"; case 1 -> "提交"; case 2 -> "已审核"; case 3 -> "入库中";
            case 4 -> "已完成"; case 9 -> "取消";
            default -> String.valueOf(s);
        };
    }

    private String arStatusText(Integer s) {
        if (s == null) return "";
        return switch (s) {
            case 0 -> "待对账"; case 1 -> "对账中"; case 2 -> "对账完成"; case 3 -> "部分收"; case 4 -> "收款完成"; case 9 -> "作废";
            default -> String.valueOf(s);
        };
    }

    private String bizTypeText(String t) {
        if (t == null) return "";
        return switch (t) {
            case "SALES" -> "销售"; case "SALES_RETURN" -> "销售退货"; case "MANUAL" -> "手工";
            default -> t;
        };
    }
}
