package com.pcba.erp.scm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.scm.entity.FinVoucherInterface;
import com.pcba.erp.scm.service.IFinVoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "应付账-财务凭证接口")
@RestController
@RequestMapping("/scm/fin-voucher")
@RequiredArgsConstructor
public class FinVoucherController {

    private final IFinVoucherService service;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('scm:fvoucher:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String voucherNo,
                                 @RequestParam(required = false) String voucherType,
                                 @RequestParam(required = false) String bizSource,
                                 @RequestParam(required = false) Long bizId,
                                 @RequestParam(required = false) Integer pushStatus,
                                 @RequestParam(required = false) String voucherDate) {
        IPage<FinVoucherInterface> p = service.selectPage(pageNum, pageSize, voucherNo, voucherType, bizSource, bizId, pushStatus, voucherDate);
        TableDataInfo t = new TableDataInfo();
        t.setRows(p.getRecords()); t.setTotal(p.getTotal()); t.setCode(200); t.setMsg("查询成功");
        return R.ok(t);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('scm:fvoucher:list')")
    @GetMapping("/{id}")
    public R<FinVoucherInterface> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('scm:fvoucher:add')")
    @OperLog(title = "FinVoucher", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody FinVoucherInterface entity) {
        Long id = service.save(entity); return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('scm:fvoucher:edit')")
    @OperLog(title = "FinVoucher", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody FinVoucherInterface entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('scm:fvoucher:remove')")
    @OperLog(title = "FinVoucher", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "下拉")
    @PreAuthorize("@ps.hasPermi('scm:fvoucher:list')")
    @GetMapping("/options")
    public R<List<FinVoucherInterface>> options() { return R.ok(service.options()); }

    // 业务动作
    @Operation(summary = "推送应付凭证(按应付单)")
    @PreAuthorize("@ps.hasPermi('scm:fvoucher:push')")
    @OperLog(title = "推送应付凭证", businessType = 2)
    @PostMapping("/push/payable/{payableId}")
    public R<Long> pushPayable(@PathVariable Long payableId) {
        Long vid = service.pushPayableVoucher(payableId);
        return vid == null ? R.fail("推送失败") : R.ok(vid);
    }

    @Operation(summary = "推送付款凭证(按付款单)")
    @PreAuthorize("@ps.hasPermi('scm:fvoucher:push')")
    @OperLog(title = "推送付款凭证", businessType = 2)
    @PostMapping("/push/payment/{paymentId}")
    public R<Long> pushPayment(@PathVariable Long paymentId) {
        Long vid = service.pushPaymentVoucher(paymentId);
        return vid == null ? R.fail("推送失败") : R.ok(vid);
    }

    @Operation(summary = "模拟推送成功(更新push_status=2)")
    @PreAuthorize("@ps.hasPermi('scm:fvoucher:push')")
    @OperLog(title = "模拟推送凭证", businessType = 2)
    @PutMapping("/mock-push/{id}")
    public R<Integer> mockPush(@PathVariable Long id) {
        return service.mockPush(id) ? R.ok(1) : R.fail("推送失败");
    }

    @Operation(summary = "作废凭证")
    @PreAuthorize("@ps.hasPermi('scm:fvoucher:cancel')")
    @OperLog(title = "作废凭证", businessType = 2)
    @PutMapping("/cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return service.cancelVoucher(id, reason) ? R.ok(1) : R.fail("作废失败");
    }
}
