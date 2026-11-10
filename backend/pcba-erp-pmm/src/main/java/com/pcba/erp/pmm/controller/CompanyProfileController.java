package com.pcba.erp.pmm.controller;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.pmm.entity.CompanyProfile;
import com.pcba.erp.pmm.service.IPmmCompanyProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 公司档案 Controller (单行表)
 * 权限前缀: company:profile:*
 */
@Tag(name = "公司档案")
@RestController
@RequestMapping("/company/profile")
@RequiredArgsConstructor
public class CompanyProfileController {

    private final IPmmCompanyProfileService profileService;

    @Operation(summary = "获取公司档案")
    @PreAuthorize("@ps.hasPermi('company:profile:list')")
    @GetMapping
    public R<CompanyProfile> get() {
        return R.ok(profileService.get());
    }

    /** 出库打印仅需要抬头，不返回税号、银行账号等完整档案。 */
    @Operation(summary = "获取出库单打印抬头")
    @PreAuthorize("@ps.hasPermi('wms:stockout:list')")
    @GetMapping("/stock-out-print")
    public R<StockOutPrintProfile> stockOutPrint() {
        CompanyProfile profile = profileService.get();
        return R.ok(new StockOutPrintProfile(profile.getCompanyName(), profile.getAddress(), profile.getContactPhone()));
    }

    public record StockOutPrintProfile(String companyName, String address, String contactPhone) {}

    @Operation(summary = "获取采购订单打印抬头")
    @PreAuthorize("@ps.hasPermi('scm:purchase:list')")
    @GetMapping("/purchase-order-print")
    public R<StockOutPrintProfile> purchaseOrderPrint() {
        CompanyProfile profile = profileService.get();
        return R.ok(new StockOutPrintProfile(profile.getCompanyName(), profile.getAddress(), profile.getContactPhone()));
    }

    @Operation(summary = "保存公司档案")
    @PreAuthorize("@ps.hasPermi('company:profile:save')")
    @OperLog(title = "公司档案-保存", businessType = 2)
    @PutMapping
    public R<Void> save(@Validated @RequestBody CompanyProfile profile) {
        profileService.save(profile);
        return R.ok();
    }
}
