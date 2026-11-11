package com.pcba.erp.sal.controller;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.sal.service.impl.SalQuotationService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sal/quotation")
@RequiredArgsConstructor
public class SalQuotationController {
    private final SalQuotationService service;
    @GetMapping("/list") @PreAuthorize("@ps.hasPermi('sal:price:list')")
    public R<Map<String,Object>> list(@RequestParam(defaultValue="1") int pageNum, @RequestParam(defaultValue="20") int pageSize, @RequestParam(required=false) Long customerId, @RequestParam(required=false) Long materialId, @RequestParam(required=false) String currency, @RequestParam(required=false) Integer status, @RequestParam(defaultValue="false") boolean onlyValid) { return R.ok(service.list(pageNum,pageSize,customerId,materialId,currency,status,onlyValid)); }
    @GetMapping("/{id}") @PreAuthorize("@ps.hasPermi('sal:price:list')")
    public R<Map<String,Object>> detail(@PathVariable Long id) { return R.ok(service.detail(id)); }
    @PostMapping @PreAuthorize("@ps.hasPermi('sal:price:add')") @OperLog(title="新增销售报价单",businessType=1)
    public R<Long> create(@RequestBody SalQuotationService.Input input) { return R.ok(service.save(null,input)); }
    @PutMapping("/{id}") @PreAuthorize("@ps.hasPermi('sal:price:edit')") @OperLog(title="修改销售报价单",businessType=2)
    public R<Long> update(@PathVariable Long id,@RequestBody SalQuotationService.Input input) { return R.ok(service.save(id,input)); }
    @DeleteMapping("/{id}") @PreAuthorize("@ps.hasPermi('sal:price:remove')") @OperLog(title="删除销售报价单",businessType=3)
    public R<Void> remove(@PathVariable Long id) { service.remove(id); return R.ok(); }
    @PutMapping("/{id}/submit") @PreAuthorize("@ps.hasPermi('sal:price:submit')") @OperLog(title="提交销售报价单",businessType=2)
    public R<Void> submit(@PathVariable Long id) { service.transition(id,"submit",null); return R.ok(); }
    @PutMapping("/{id}/audit") @PreAuthorize("@ps.hasPermi('sal:price:audit')") @OperLog(title="审核销售报价单",businessType=2)
    public R<Void> audit(@PathVariable Long id) { service.transition(id,"audit",null); return R.ok(); }
    @PutMapping("/{id}/reject") @PreAuthorize("@ps.hasPermi('sal:price:reject')") @OperLog(title="驳回销售报价单",businessType=2)
    public R<Void> reject(@PathVariable Long id,@RequestBody Map<String,String> body) { service.transition(id,"reject",body.get("reason")); return R.ok(); }
    @PutMapping("/{id}/disable") @PreAuthorize("@ps.hasPermi('sal:price:edit')") @OperLog(title="停用销售报价单",businessType=2)
    public R<Void> disable(@PathVariable Long id) { service.transition(id,"disable",null); return R.ok(); }
    @PutMapping("/{id}/enable") @PreAuthorize("@ps.hasPermi('sal:price:edit')") @OperLog(title="启用销售报价单",businessType=2)
    public R<Void> enable(@PathVariable Long id) { service.transition(id,"enable",null); return R.ok(); }
}
