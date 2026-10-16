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

    @Operation(summary = "保存公司档案")
    @PreAuthorize("@ps.hasPermi('company:profile:save')")
    @OperLog(title = "公司档案-保存", businessType = 2)
    @PutMapping
    public R<Void> save(@Validated @RequestBody CompanyProfile profile) {
        profileService.save(profile);
        return R.ok();
    }
}
