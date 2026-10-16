package com.pcba.erp.sal.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.sal.entity.SalCustomer;
import com.pcba.erp.sal.service.ISalCustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "销售-customer")
@RestController
@RequestMapping("/sal/customer")
@RequiredArgsConstructor
public class SalCustomerController {

    private final ISalCustomerService service;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('sal:customer:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String customerCode, @RequestParam(required = false) String customerName, @RequestParam(required = false) String contact, @RequestParam(required = false) String phone, @RequestParam(required = false) String industry, @RequestParam(required = false) String level, @RequestParam(required = false) Integer status) {
        IPage<SalCustomer> page = service.selectPage(pageNum, pageSize, customerCode, customerName, contact, phone, industry, level, status);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('sal:customer:list')")
    @GetMapping("/{id}")
    public R<SalCustomer> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增")
    @PreAuthorize("@ps.hasPermi('sal:customer:add')")
    @OperLog(title = "SalCustomer", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody SalCustomer entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改")
    @PreAuthorize("@ps.hasPermi('sal:customer:edit')")
    @OperLog(title = "SalCustomer", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody SalCustomer entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除")
    @PreAuthorize("@ps.hasPermi('sal:customer:remove')")
    @OperLog(title = "SalCustomer", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "启用")
    @PreAuthorize("@ps.hasPermi('sal:customer:edit')")
    @OperLog(title = "启用", businessType = 2)
    @PutMapping("enable/{id}")
    public R<Integer> enable(@PathVariable Long id) { return service.changeStatus(id, 1) ? R.ok(1) : R.fail("启用失败"); }
    @Operation(summary = "停用")
    @PreAuthorize("@ps.hasPermi('sal:customer:edit')")
    @OperLog(title = "停用", businessType = 2)
    @PutMapping("disable/{id}")
    public R<Integer> disable(@PathVariable Long id) { return service.changeStatus(id, 0) ? R.ok(1) : R.fail("停用失败"); }

    @Operation(summary = "下拉选项")
    @PreAuthorize("@ps.hasPermi('sal:customer:list')")
    @GetMapping("/options")
    public R<List<SalCustomer>> options() { return R.ok(service.options()); }

    @Operation(summary = "生成下一个客户编码")
    @PreAuthorize("@ps.hasPermi('sal:customer:add')")
    @GetMapping("/next-code")
    public R<String> nextCode() { return R.ok(service.nextCustomerCode()); }

    @Operation(summary = "客户信用占用查询(额度/未收/逾期/已用/可用)")
    @PreAuthorize("@ps.hasPermi('sal:customer:list')")
    @GetMapping("/{id}/credit")
    public R<Map<String, Object>> credit(@PathVariable Long id) { return R.ok(service.getCredit(id)); }
}
