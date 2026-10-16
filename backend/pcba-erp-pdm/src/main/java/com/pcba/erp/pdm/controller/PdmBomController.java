package com.pcba.erp.pdm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.pdm.entity.PdmBom;
import com.pcba.erp.pdm.entity.PdmBomLine;
import com.pcba.erp.pdm.service.IPdmBomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Tag(name = "BOM 管理")
@RestController
@RequestMapping("/pdm/bom")
@RequiredArgsConstructor
public class PdmBomController {

    private final IPdmBomService bomService;

    /* ============== 列表/详情 ============== */

    @Operation(summary = "BOM 分页查询")
    @PreAuthorize("@ps.hasPermi('pdm:bom:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String bomCode,
                                 @RequestParam(required = false) String bomName,
                                 @RequestParam(required = false) String productMaterialCode,
                                 @RequestParam(required = false) Long productMaterialId,
                                 @RequestParam(required = false) Long categoryId,
                                 @RequestParam(required = false) Integer bomType,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) String version) {
        IPage<PdmBom> page = bomService.selectBomPage(pageNum, pageSize, bomCode, bomName,
                productMaterialCode, productMaterialId, categoryId, bomType, status, version);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "BOM 详情（含行项）")
    @PreAuthorize("@ps.hasPermi('pdm:bom:list')")
    @GetMapping("/{id}")
    public R<PdmBom> get(@PathVariable Long id) {
        return R.ok(bomService.getDetail(id));
    }

    @Operation(summary = "生成新 BOM 编码")
    @PreAuthorize("@ps.hasPermi('pdm:bom:add')")
    @GetMapping("/next-code")
    public R<String> nextCode() { return R.ok(bomService.nextBomCode()); }

    /* ============== CRUD ============== */

    @Operation(summary = "新增BOM（主档+行项一次性保存，返回BOM ID）")
    @PreAuthorize("@ps.hasPermi('pdm:bom:add')")
    @OperLog(title = "BOM管理", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody PdmBom bom) {
        Long id = bomService.saveWithLines(bom);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改BOM主档（已启用/已归档不可改）")
    @PreAuthorize("@ps.hasPermi('pdm:bom:edit')")
    @OperLog(title = "BOM管理", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody PdmBom bom) {
        return bomService.update(bom) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除BOM（仅草稿/驳回可删，级联删除行项）")
    @PreAuthorize("@ps.hasPermi('pdm:bom:remove')")
    @OperLog(title = "BOM管理", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return bomService.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "全量覆盖行项（replaceAll，草稿BOM允许）")
    @PreAuthorize("@ps.hasPermi('pdm:bom:edit')")
    @OperLog(title = "BOM管理-行项", businessType = 2)
    @PutMapping("/replace-lines/{bomId}")
    public R<Integer> replaceLines(@PathVariable Long bomId,
                                   @RequestBody(required = false) List<PdmBomLine> lines) {
        return bomService.replaceLines(bomId, lines) ? R.ok(1) : R.fail("保存失败");
    }

    /* ============== 状态机 ============== */

    @Operation(summary = "提交审核 草稿/驳回→审批中")
    @PreAuthorize("@ps.hasPermi('pdm:bom:audit')")
    @OperLog(title = "BOM管理", businessType = 4)
    @PutMapping("/submit-audit/{id}")
    public R<Integer> submitAudit(@PathVariable Long id) {
        return bomService.submitAudit(id) ? R.ok(1) : R.fail("提交失败");
    }

    @Operation(summary = "审核通过 审批中→启用")
    @PreAuthorize("@ps.hasPermi('pdm:bom:audit')")
    @OperLog(title = "BOM管理", businessType = 4)
    @PutMapping("/audit-pass/{id}")
    public R<Integer> auditPass(@PathVariable Long id) {
        String by = SecurityUtils.getUsernameSafe();
        return bomService.auditPass(id, by) ? R.ok(1) : R.fail("通过失败");
    }

    @Operation(summary = "审核驳回 审批中→驳回")
    @PreAuthorize("@ps.hasPermi('pdm:bom:audit')")
    @OperLog(title = "BOM管理", businessType = 4)
    @PutMapping("/audit-reject/{id}")
    public R<Integer> auditReject(@PathVariable Long id,
                                  @RequestParam(required = false) String rejectReason) {
        String by = SecurityUtils.getUsernameSafe();
        return bomService.auditReject(id, by, rejectReason) ? R.ok(1) : R.fail("驳回失败");
    }

    @Operation(summary = "停用 启用→停用")
    @PreAuthorize("@ps.hasPermi('pdm:bom:edit')")
    @OperLog(title = "BOM管理", businessType = 2)
    @PutMapping("/disable/{id}")
    public R<Integer> disable(@PathVariable Long id) {
        return bomService.disable(id) ? R.ok(1) : R.fail("停用失败");
    }

    @Operation(summary = "归档 停用→归档")
    @PreAuthorize("@ps.hasPermi('pdm:bom:edit')")
    @OperLog(title = "BOM管理", businessType = 2)
    @PutMapping("/archive/{id}")
    public R<Integer> archive(@PathVariable Long id) {
        return bomService.archive(id) ? R.ok(1) : R.fail("归档失败");
    }

    /* ============== 多层展开 ============== */

    @Operation(summary = "单层行项（直接返回BOM下所有行，不分层）")
    @PreAuthorize("@ps.hasPermi('pdm:bom:list')")
    @GetMapping("/lines/single/{bomId}")
    public R<List<PdmBomLine>> linesSingle(@PathVariable Long bomId) {
        return R.ok(bomService.listSingleLevel(bomId));
    }

    @Operation(summary = "多层递归展开（返回树列表，含level/pathQty；最多levelMax层，默认10）")
    @PreAuthorize("@ps.hasPermi('pdm:bom:explode')")
    @GetMapping("/explode/tree/{bomId}")
    public R<List<PdmBomLine>> explodeTree(@PathVariable Long bomId,
                                           @RequestParam(defaultValue = "10") int levelMax,
                                           @RequestParam(defaultValue = "true") boolean phantomExpand) {
        return R.ok(bomService.explodeTree(bomId, levelMax, phantomExpand));
    }

    @Operation(summary = "扁平化用量汇总(叶子物料累计pathQty) — MRP/采购计算用")
    @PreAuthorize("@ps.hasPermi('pdm:bom:explode')")
    @GetMapping("/explode/flat/{bomId}")
    public R<Map<Long, PdmBomLine>> explodeFlat(@PathVariable Long bomId,
                                                @RequestParam(defaultValue = "10") int levelMax,
                                                @RequestParam(defaultValue = "true") boolean phantomExpand) {
        return R.ok(bomService.explodeFlatSum(bomId, levelMax, phantomExpand));
    }

    /* ============== Where-Used 反查 ============== */

    @Operation(summary = "Where-Used 单层反查：子件X在哪些BOM作为子件")
    @PreAuthorize("@ps.hasPermi('pdm:bom:whereused')")
    @GetMapping("/where-used/single")
    public R<List<PdmBomLine>> whereUsedSingle(@RequestParam Long componentId,
                                               @RequestParam(required = false) Integer lineStatus,
                                               @RequestParam(required = false) Integer bomStatus) {
        return R.ok(bomService.whereUsedSingle(componentId, lineStatus, bomStatus));
    }

    @Operation(summary = "Where-Used 递归反查：一直到顶层成品（返回扁平化路径信息）")
    @PreAuthorize("@ps.hasPermi('pdm:bom:whereused')")
    @GetMapping("/where-used/recursive")
    public R<List<Map<String, Object>>> whereUsedRecursive(@RequestParam Long componentId,
                                                           @RequestParam(required = false) Integer lineStatus,
                                                           @RequestParam(required = false) Integer bomStatus,
                                                           @RequestParam(defaultValue = "10") int levelMax) {
        return R.ok(bomService.whereUsedRecursive(componentId, lineStatus, bomStatus, levelMax));
    }

    /* ============== 批量替换 ============== */

    @Operation(summary = "批量替换某BOM中子件 old→new，返回更新行数")
    @PreAuthorize("@ps.hasPermi('pdm:bom:edit')")
    @OperLog(title = "BOM管理-替换子件", businessType = 2)
    @PutMapping("/replace-component/{bomId}")
    public R<Integer> replaceComponent(@PathVariable Long bomId,
                                       @RequestParam Long oldComponentId,
                                       @RequestParam Long newComponentId,
                                       @RequestParam(defaultValue = "true") boolean keepAltGroup,
                                       @RequestParam(required = false) String remark) {
        int n = bomService.replaceComponent(bomId, oldComponentId, newComponentId, keepAltGroup, remark);
        return R.ok(n);
    }

    /* ============== Excel 导入 ============== */

    @Operation(summary = "下载 BOM 导入模板")
    @PreAuthorize("@ps.hasPermi('pdm:bom:add')")
    @GetMapping("/import/template")
    public void downloadBomTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String fileName = URLEncoder.encode("BOM行项导入模板.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        bomService.exportBomTemplate(response.getOutputStream());
    }

    @Operation(summary = "预览 BOM 导入（解析+校验，不入库）")
    @PreAuthorize("@ps.hasPermi('pdm:bom:add')")
    @PostMapping("/import/preview")
    public R<Map<String, Object>> previewBomImport(@RequestParam("file") MultipartFile file,
                                                     @RequestParam Long productMaterialId,
                                                     @RequestParam(required = false) String bomName,
                                                     @RequestParam(required = false) String version) throws IOException {
        return R.ok(bomService.previewBomImport(file, productMaterialId, bomName, version));
    }

    @Operation(summary = "确认 BOM 导入（创建草稿物料+BOM头+行项）")
    @PreAuthorize("@ps.hasPermi('pdm:bom:add')")
    @OperLog(title = "BOM管理-Excel导入", businessType = 1)
    @PostMapping("/import/confirm")
    public R<Long> confirmBomImport(@RequestBody Map<String, Object> previewData) {
        Long bomId = bomService.confirmBomImport(previewData);
        return bomId == null ? R.fail("导入失败") : R.ok(bomId);
    }
}
