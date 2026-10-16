package com.pcba.erp.pdm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.pdm.entity.PdmMaterial;
import com.pcba.erp.pdm.excel.MaterialImportResult;
import com.pcba.erp.pdm.service.IPdmMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 物料主档 Controller
 *   - 基础 CRUD + 状态机 (提交审核/通过/驳回/停用)
 *   - W3.2 再接入 导入/导出/编码生成器独立接口
 */
@Tag(name = "物料主档管理")
@RestController
@RequestMapping("/pdm/material")
@RequiredArgsConstructor
public class PdmMaterialController {

    private final IPdmMaterialService materialService;

    @Operation(summary = "物料分页查询 (左侧分类树筛选+条件查询)")
    @PreAuthorize("@ps.hasPermi('pdm:material:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String materialType,
            @RequestParam(required = false) String mpn,
            @RequestParam(required = false) Integer status) {
        IPage<PdmMaterial> page = materialService.selectMaterialPage(
                pageNum, pageSize, categoryId, materialCode, materialName,
                materialType, mpn, status);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "物料详情")
    @PreAuthorize("@ps.hasPermi('pdm:material:list')")
    @GetMapping("/{id}")
    public R<PdmMaterial> get(@PathVariable Long id) {
        return R.ok(materialService.getById(id));
    }

    @Operation(summary = "新增物料 (默认草稿, 编码可手填或通过 /pdm/code/generate 生成)，返回新物料ID")
    @PreAuthorize("@ps.hasPermi('pdm:material:add')")
    @OperLog(title = "物料主档", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody PdmMaterial material) {
        boolean ok = materialService.save(material);
        if (!ok || material.getMaterialId() == null) return R.fail("新增失败");
        return R.ok(material.getMaterialId());
    }

    @Operation(summary = "修改物料")
    @PreAuthorize("@ps.hasPermi('pdm:material:edit')")
    @OperLog(title = "物料主档", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody PdmMaterial material) {
        return materialService.updateById(material) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除物料 (仅草稿/驳回可删)")
    @PreAuthorize("@ps.hasPermi('pdm:material:remove')")
    @OperLog(title = "物料主档", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return materialService.removeById(id) ? R.ok(1) : R.fail("删除失败");
    }

    // ============== 状态机 ==============
    @Operation(summary = "提交审核 (草稿/驳回 → 审核中)")
    @PreAuthorize("@ps.hasPermi('pdm:material:audit')")
    @OperLog(title = "物料主档", businessType = 4)
    @PutMapping("/submit-audit/{id}")
    public R<Integer> submitAudit(@PathVariable Long id) {
        return materialService.submitAudit(id) ? R.ok(1) : R.fail("提交失败");
    }

    @Operation(summary = "审核通过 (审核中 → 已启用)")
    @PreAuthorize("@ps.hasPermi('pdm:material:audit')")
    @OperLog(title = "物料主档", businessType = 4)
    @PutMapping("/audit-pass/{id}")
    public R<Integer> auditPass(@PathVariable Long id) {
        String by = SecurityUtils.getUsernameSafe();
        return materialService.auditPass(id, by) ? R.ok(1) : R.fail("审核失败");
    }

    @Operation(summary = "审核驳回 (审核中 → 已驳回)")
    @PreAuthorize("@ps.hasPermi('pdm:material:audit')")
    @OperLog(title = "物料主档", businessType = 4)
    @PutMapping("/audit-reject/{id}")
    public R<Integer> auditReject(@PathVariable Long id,
                                   @RequestParam(required = false) String rejectReason) {
        String by = SecurityUtils.getUsernameSafe();
        return materialService.auditReject(id, by, rejectReason) ? R.ok(1) : R.fail("驳回失败");
    }

    @Operation(summary = "停用物料 (已启用 → 已失效)")
    @PreAuthorize("@ps.hasPermi('pdm:material:edit')")
    @OperLog(title = "物料主档", businessType = 2)
    @PutMapping("/disable/{id}")
    public R<Integer> disable(@PathVariable Long id) {
        return materialService.disable(id) ? R.ok(1) : R.fail("停用失败");
    }

    /* ================ W3.2 导入导出 ================ */

    @Operation(summary = "下载物料导入模板 (带下拉校验+示例行)")
    @PreAuthorize("@ps.hasPermi('pdm:material:import')")
    @GetMapping("/import/template")
    public void downloadTemplate(HttpServletResponse resp) throws IOException {
        String name = "物料导入模板.xlsx";
        setExcelResp(resp, name);
        materialService.exportTemplate(resp.getOutputStream());
    }

    @Operation(summary = "导出物料主档 (按当前查询条件，最多10000条)")
    @PreAuthorize("@ps.hasPermi('pdm:material:export')")
    @OperLog(title = "物料主档", businessType = 5) // EXPORT
    @GetMapping("/export")
    public void export(HttpServletResponse resp,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) String materialCode,
                       @RequestParam(required = false) String materialName,
                       @RequestParam(required = false) String materialType,
                       @RequestParam(required = false) String mpn,
                       @RequestParam(required = false) Integer status) throws IOException {
        String name = "物料主档_" + System.currentTimeMillis() + ".xlsx";
        setExcelResp(resp, name);
        materialService.exportToStream(resp.getOutputStream(), categoryId, materialCode,
                materialName, materialType, mpn, status);
    }

    @Operation(summary = "导入物料 (新增/更新 批量)")
    @PreAuthorize("@ps.hasPermi('pdm:material:import')")
    @OperLog(title = "物料主档", businessType = 6) // IMPORT
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<MaterialImportResult> doImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean updateSupport) throws IOException {
        if (file == null || file.isEmpty()) return R.fail("上传文件不能为空");
        String fn = file.getOriginalFilename();
        if (fn == null || !(fn.endsWith(".xlsx") || fn.endsWith(".xls"))) {
            return R.fail("仅支持 .xlsx / .xls 格式");
        }
        return R.ok(materialService.importMaterials(file, updateSupport));
    }

    /* ================= 私有 ================= */
    private void setExcelResp(HttpServletResponse resp, String filename) {
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setCharacterEncoding("utf-8");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        resp.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + encoded);
        resp.setHeader("Access-Control-Expose-Headers", HttpHeaders.CONTENT_DISPOSITION);
    }
}
