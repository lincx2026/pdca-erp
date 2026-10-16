package com.pcba.erp.sal.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.sal.entity.SalForecast;
import com.pcba.erp.sal.service.ISalForecastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 销售预测/滚动计划 Controller
 * 状态机: 0草稿 → 1生效 → 2已归档
 * 权限码: sal:forecast:*
 */
@Tag(name = "销售-预测")
@RestController
@RequestMapping("/sal/forecast")
@RequiredArgsConstructor
public class SalForecastController {

    private final ISalForecastService service;

    @Operation(summary = "分页查询")
    @PreAuthorize("@ps.hasPermi('sal:forecast:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String versionNo,
                                 @RequestParam(required = false) String versionType,
                                 @RequestParam(required = false) Long customerId,
                                 @RequestParam(required = false) Long materialId,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) String customerName,
                                 @RequestParam(required = false) String materialName) {
        IPage<SalForecast> page = service.selectPage(pageNum, pageSize, versionNo, versionType,
                customerId, materialId, status, customerName, materialName);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "详情")
    @PreAuthorize("@ps.hasPermi('sal:forecast:list')")
    @GetMapping("/{id}")
    public R<SalForecast> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增(单条,草稿)")
    @PreAuthorize("@ps.hasPermi('sal:forecast:edit')")
    @OperLog(title = "销售预测", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody SalForecast entity) {
        return R.ok(service.save(entity));
    }

    @Operation(summary = "修改(仅草稿)")
    @PreAuthorize("@ps.hasPermi('sal:forecast:edit')")
    @OperLog(title = "销售预测", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody SalForecast entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除(仅草稿)")
    @PreAuthorize("@ps.hasPermi('sal:forecast:remove')")
    @OperLog(title = "销售预测", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "版本列表(下拉用)")
    @PreAuthorize("@ps.hasPermi('sal:forecast:list')")
    @GetMapping("/versions")
    public R<List<Map<String, Object>>> versions() { return R.ok(service.listVersions()); }

    @Operation(summary = "生成新版本(滚动计划核心)")
    @PreAuthorize("@ps.hasPermi('sal:forecast:generate')")
    @OperLog(title = "销售预测-生成版本", businessType = 1)
    @PostMapping("/generate")
    public R<Integer> generate(@RequestParam(defaultValue = "W") String versionType,
                               @RequestParam(defaultValue = "12") int periodsAhead) {
        int rows = service.generateVersion(versionType, periodsAhead, currentUser());
        return rows > 0 ? R.ok(rows) : R.fail("生成失败:无足够历史数据或版本已存在");
    }

    @Operation(summary = "回写实际值(已过去周期)")
    @PreAuthorize("@ps.hasPermi('sal:forecast:edit')")
    @OperLog(title = "销售预测-回写实际", businessType = 2)
    @PutMapping("/rollback")
    public R<Integer> rollback(@RequestParam String versionNo) {
        return R.ok(service.rollbackActual(versionNo));
    }

    @Operation(summary = "版本审核生效(0→1)")
    @PreAuthorize("@ps.hasPermi('sal:forecast:audit')")
    @OperLog(title = "销售预测-审核", businessType = 2)
    @PutMapping("/audit")
    public R<Integer> audit(@RequestParam String versionNo) {
        return service.auditVersion(versionNo, currentUser()) ? R.ok(1) : R.fail("审核失败");
    }

    @Operation(summary = "版本归档(1→2)")
    @PreAuthorize("@ps.hasPermi('sal:forecast:archive')")
    @OperLog(title = "销售预测-归档", businessType = 2)
    @PutMapping("/archive")
    public R<Integer> archive(@RequestParam String versionNo) {
        return service.archiveVersion(versionNo) ? R.ok(1) : R.fail("归档失败");
    }

    @Operation(summary = "手工调整预测量(仅草稿,自动重算金额)")
    @PreAuthorize("@ps.hasPermi('sal:forecast:edit')")
    @OperLog(title = "销售预测-调整", businessType = 2)
    @PutMapping("/adjust")
    public R<Integer> adjust(@RequestParam Long forecastId, @RequestParam BigDecimal newQty) {
        return service.adjustQty(forecastId, newQty, currentUser()) ? R.ok(1) : R.fail("调整失败");
    }

    /* ============ 看板 ============ */
    @Operation(summary = "看板汇总(当前生效版本)")
    @PreAuthorize("@ps.hasPermi('sal:forecast:kanban')")
    @GetMapping("/kanban/summary")
    public R<Map<String, Object>> kanbanSummary(@RequestParam(required = false) Long customerId) {
        return R.ok(service.kanbanSummary(customerId));
    }

    @Operation(summary = "看板趋势(按周期分桶 预测vs实际)")
    @PreAuthorize("@ps.hasPermi('sal:forecast:kanban')")
    @GetMapping("/kanban/trend")
    public R<List<Map<String, Object>>> trend(@RequestParam(required = false) Long customerId,
                                                 @RequestParam(defaultValue = "12") int periods) {
        return R.ok(service.forecastTrend(customerId, periods));
    }

    @Operation(summary = "看板Top客户(按预测金额降序)")
    @PreAuthorize("@ps.hasPermi('sal:forecast:kanban')")
    @GetMapping("/kanban/top-customers")
    public R<List<Map<String, Object>>> topCustomers(@RequestParam(required = false) Long customerId,
                                                        @RequestParam(defaultValue = "10") int limit) {
        return R.ok(service.topCustomers(customerId, limit));
    }

    @Operation(summary = "看板Top物料(按预测数量降序)")
    @PreAuthorize("@ps.hasPermi('sal:forecast:kanban')")
    @GetMapping("/kanban/top-materials")
    public R<List<Map<String, Object>>> topMaterials(@RequestParam(required = false) Long customerId,
                                                       @RequestParam(defaultValue = "10") int limit) {
        return R.ok(service.topMaterials(customerId, limit));
    }

    @Operation(summary = "导出(按当前筛选,最多10000条)")
    @PreAuthorize("@ps.hasPermi('sal:forecast:export')")
    @OperLog(title = "销售预测", businessType = 5)
    @GetMapping("/export")
    public void export(HttpServletResponse resp,
                       @RequestParam(required = false) String versionNo,
                       @RequestParam(required = false) String versionType,
                       @RequestParam(required = false) Long customerId,
                       @RequestParam(required = false) Long materialId,
                       @RequestParam(required = false) Integer status,
                       @RequestParam(required = false) String customerName,
                       @RequestParam(required = false) String materialName) throws IOException {
        setExcelResp(resp, "销售预测_" + System.currentTimeMillis() + ".xlsx");
        IPage<SalForecast> page = service.selectPage(1, 10000, versionNo, versionType,
                customerId, materialId, status, customerName, materialName);
        EasyExcel.write(resp.getOutputStream(), SalForecast.class).sheet("销售预测").doWrite(page.getRecords());
    }

    private String currentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) { return ""; }
    }

    private void setExcelResp(HttpServletResponse resp, String filename) throws IOException {
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setCharacterEncoding("utf-8");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        resp.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + encoded);
        resp.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION);
    }
}
