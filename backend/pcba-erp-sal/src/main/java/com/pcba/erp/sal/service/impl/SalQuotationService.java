package com.pcba.erp.sal.service.impl;

import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.sal.entity.SalCustomerPrice;
import com.pcba.erp.sal.service.ISalCustomerPriceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalQuotationService {
    private final JdbcTemplate jdbc;
    private final org.mybatis.spring.SqlSessionTemplate sqlSession;
    private final ISalCustomerPriceService prices;
    public record Input(String title, Long customerId, String currency, LocalDate validFrom, LocalDate validTo,
                        String remark, List<SalCustomerPrice> items) {}

    public Map<String, Object> list(int pageNum, int pageSize, Long customerId, Long materialId, String currency, Integer status, boolean onlyValid) {
        int size = Math.min(Math.max(pageSize, 1), 100), offset = (Math.max(pageNum, 1) - 1) * size;
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        var args = new java.util.ArrayList<Object>();
        if (customerId != null) { where.append(" AND q.customer_id=? "); args.add(customerId); }
        if (materialId != null) { where.append(" AND EXISTS (SELECT 1 FROM sal_customer_price p WHERE p.quotation_id=q.quotation_id AND p.material_id=?) "); args.add(materialId); }
        if (currency != null && !currency.isBlank()) { where.append(" AND q.currency=? "); args.add(currency); }
        if (status != null) { where.append(" AND q.status=? "); args.add(status); }
        if (onlyValid) where.append(" AND q.status=2 AND q.valid_from<=CURRENT_DATE AND q.valid_to>=CURRENT_DATE ");
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM sal_quotation q"+where,Long.class,args.toArray());
        args.add(size); args.add(offset);
        var rows = jdbc.queryForList("SELECT q.quotation_id AS \"quotationId\",q.quotation_no AS \"quotationNo\",q.title,q.customer_id AS \"customerId\",c.customer_name AS \"customerName\",q.currency,q.valid_from AS \"validFrom\",q.valid_to AS \"validTo\",q.status,q.remark,(SELECT COUNT(*) FROM sal_customer_price p WHERE p.quotation_id=q.quotation_id) AS \"lineCount\" FROM sal_quotation q LEFT JOIN scm_customer c ON c.customer_id=q.customer_id"+where+" ORDER BY q.quotation_id DESC LIMIT ? OFFSET ?", args.toArray());
        return Map.of("rows", rows, "total", total == null ? 0 : total);
    }
    public Map<String, Object> detail(Long id) {
        var headers = jdbc.queryForList("SELECT quotation_id AS \"quotationId\",quotation_no AS \"quotationNo\",title,customer_id AS \"customerId\",currency,valid_from AS \"validFrom\",valid_to AS \"validTo\",status,remark FROM sal_quotation WHERE quotation_id=?", id);
        if (headers.isEmpty()) throw new ServiceException("报价单不存在");
        var items = jdbc.queryForList("SELECT price_id AS \"priceId\",material_id AS \"materialId\",material_code AS \"materialCode\",material_name AS \"materialName\",specification,unit_price AS \"unitPrice\",tax_rate AS \"taxRate\",min_qty AS \"minQty\" FROM sal_customer_price WHERE quotation_id=? ORDER BY price_id", id);
        return Map.of("header", headers.get(0), "items", items);
    }
    @Transactional(rollbackFor = Exception.class)
    public Long save(Long id, Input input) {
        if (input == null || input.title() == null || input.title().isBlank() || input.title().length()>200 || input.customerId()==null || input.validFrom()==null || input.validTo()==null || input.validFrom().isAfter(input.validTo())) throw new ServiceException("请填写报价名称、客户和有效日期");
        if (input.items()==null || input.items().isEmpty() || input.items().size()>100) throw new ServiceException("请选择1至100条报价明细");
        if (input.remark()!=null && input.remark().length()>500) throw new ServiceException("备注过长");
        String currency = input.currency()==null || input.currency().isBlank() ? "CNY" : input.currency().trim();
        if (currency.length()>10) throw new ServiceException("币种过长");
        var unique = new java.util.HashSet<String>();
        for (var item : input.items()) {
            if (item.getMaterialId()==null || item.getUnitPrice()==null || item.getUnitPrice().signum()<0 || item.getTaxRate()==null || item.getTaxRate().signum()<0 || item.getTaxRate().compareTo(new BigDecimal("100"))>0 || (item.getMinQty()!=null && item.getMinQty().signum()<0)) throw new ServiceException("明细物料、含税单价、税率或起订量无效");
            if (!unique.add(item.getMaterialId()+":"+(item.getMinQty()==null ? BigDecimal.ZERO : item.getMinQty()).stripTrailingZeros())) throw new ServiceException("同一物料和起订量不能重复");
        }
        String no;
        if (id == null) {
            LocalDate today = LocalDate.now();
            Long seq = jdbc.queryForObject("INSERT INTO document_number_counter(document_type,number_date,last_number) VALUES ('SQ',?,1) ON CONFLICT(document_type,number_date) DO UPDATE SET last_number=document_number_counter.last_number+1 RETURNING last_number", Long.class,today);
            no = "SQ" + today.format(DateTimeFormatter.ofPattern("yyMMdd")) + String.format("%04d",seq);
            id = jdbc.queryForObject("INSERT INTO sal_quotation(quotation_no,title,customer_id,currency,valid_from,valid_to,remark) VALUES (?,?,?,?,?,?,?) RETURNING quotation_id",Long.class,no,input.title().trim(),input.customerId(),currency,input.validFrom(),input.validTo(),input.remark());
        } else {
            var old = jdbc.queryForList("SELECT quotation_no,status FROM sal_quotation WHERE quotation_id=? FOR UPDATE",id);
            if (old.isEmpty()) throw new ServiceException("报价单不存在");
            int status = ((Number)old.get(0).get("status")).intValue();
            if (status!=0 && status!=4) throw new ServiceException("仅草稿或已驳回报价单可编辑");
            no = String.valueOf(old.get(0).get("quotation_no"));
            jdbc.update("DELETE FROM sal_customer_price WHERE quotation_id=?",id);
            jdbc.update("UPDATE sal_quotation SET title=?,customer_id=?,currency=?,valid_from=?,valid_to=?,remark=? WHERE quotation_id=?", input.title().trim(),input.customerId(),currency,input.validFrom(),input.validTo(),input.remark(),id);
        }
        for (var item : input.items()) {
            item.setPriceId(null); item.setQuotationId(null); item.setOrderId(null); item.setOrderLineId(null);
            item.setCreateBy(null); item.setCreateTime(null); item.setUpdateBy(null); item.setUpdateTime(null);
            item.setCustomerId(input.customerId()); item.setCurrency(currency); item.setValidFrom(input.validFrom()); item.setValidTo(input.validTo());
        }
        List<Long> ids = prices.batchSave(input.items());
        for (Long priceId : ids) jdbc.update("UPDATE sal_customer_price SET quotation_id=?,quote_source=? WHERE price_id=?",id,no,priceId);
        sqlSession.clearCache();
        return id;
    }
    @Transactional(rollbackFor = Exception.class)
    public void transition(Long id, String action, String reason) {
        var rows=jdbc.queryForList("SELECT status FROM sal_quotation WHERE quotation_id=? FOR UPDATE",id);
        if (rows.isEmpty()) throw new ServiceException("报价单不存在");
        int status=((Number)rows.get(0).get("status")).intValue(), next;
        switch (action) {
            case "submit" -> { if (status!=0 && status!=4) throw new ServiceException("仅草稿或驳回报价单可提交"); next=1; }
            case "audit" -> { if (status!=1) throw new ServiceException("仅待审核报价单可审核"); next=2; }
            case "reject" -> { if (status!=1 || reason==null || reason.isBlank() || reason.length()>500) throw new ServiceException("仅待审核报价单可驳回，请填写原因"); next=4; }
            case "disable" -> { if (status!=2) throw new ServiceException("仅已审核报价单可停用"); next=3; }
            case "enable" -> { if (status!=3) throw new ServiceException("仅停用报价单可启用"); next=2; }
            default -> throw new ServiceException("未知操作");
        }
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM sal_customer_price WHERE quotation_id=?",Long.class,id);
        if (count==null || count==0) throw new ServiceException("报价单没有明细");
        String user=SecurityUtils.getUsernameSafe();
        jdbc.update("UPDATE sal_quotation SET status=?,reject_reason=?,audit_by=?,audit_time=CURRENT_TIMESTAMP WHERE quotation_id=?",next,"reject".equals(action)?reason:null,user,id);
        jdbc.update("UPDATE sal_customer_price SET status=?,reject_reason=?,audit_by=?,audit_time=CURRENT_TIMESTAMP WHERE quotation_id=?",next,"reject".equals(action)?reason:null,user,id);
        sqlSession.clearCache();
    }
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        var rows=jdbc.queryForList("SELECT status FROM sal_quotation WHERE quotation_id=? FOR UPDATE",id);
        if (rows.isEmpty()) throw new ServiceException("报价单不存在");
        int status=((Number)rows.get(0).get("status")).intValue();
        if (status!=0 && status!=4) throw new ServiceException("仅草稿或已驳回报价单可删除");
        jdbc.update("DELETE FROM sal_customer_price WHERE quotation_id=?",id);
        jdbc.update("DELETE FROM sal_quotation WHERE quotation_id=?",id);
        sqlSession.clearCache();
    }
}
