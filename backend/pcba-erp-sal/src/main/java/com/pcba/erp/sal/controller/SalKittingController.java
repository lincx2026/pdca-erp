package com.pcba.erp.sal.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.sal.entity.SalKitting;
import com.pcba.erp.sal.entity.SalKittingLine;
import com.pcba.erp.sal.service.ISalKittingLineService;
import com.pcba.erp.sal.service.ISalKittingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 销售齐套 Controller
 */
@Tag(name = "销售齐套管理")
@RestController
@RequestMapping("/sal/kitting")
@RequiredArgsConstructor
public class SalKittingController {

    private final ISalKittingService kittingService;
    private final ISalKittingLineService lineService;

    @Operation(summary = "齐套单分页查询")
    @PreAuthorize("@ps.hasPermi('sal:kitting:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String kittingNo,
            @RequestParam(required = false) String soNo,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long bomId) {
        IPage<SalKitting> page = kittingService.selectPage(pageNum, pageSize, kittingNo, soNo, customerId, status, bomId);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "齐套单详情(含明细)")
    @PreAuthorize("@ps.hasPermi('sal:kitting:list')")
    @GetMapping("/{id}")
    public R<SalKitting> get(@PathVariable Long id) {
        return R.ok(kittingService.getDetail(id));
    }

    @Operation(summary = "齐套单下拉列表")
    @PreAuthorize("@ps.hasPermi('sal:kitting:list')")
    @GetMapping("/options")
    public R<List<SalKitting>> options() {
        return R.ok(kittingService.options());
    }

    @Operation(summary = "生成齐套单号")
    @PreAuthorize("@ps.hasPermi('sal:kitting:add')")
    @GetMapping("/next-code")
    public R<String> nextCode() {
        return R.ok(kittingService.nextKittingNo());
    }

    @Operation(summary = "新增齐套单")
    @PreAuthorize("@ps.hasPermi('sal:kitting:add')")
    @OperLog(title = "销售齐套", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody SalKitting entity) {
        return R.ok(kittingService.save(entity));
    }

    @Operation(summary = "修改齐套单")
    @PreAuthorize("@ps.hasPermi('sal:kitting:edit')")
    @OperLog(title = "销售齐套", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody SalKitting entity) {
        return kittingService.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除齐套单")
    @PreAuthorize("@ps.hasPermi('sal:kitting:remove')")
    @OperLog(title = "销售齐套", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return kittingService.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    // ============ 核心动作 ============

    @Operation(summary = "齐套检查(展开BOM×套数→查库存)")
    @PreAuthorize("@ps.hasPermi('sal:kitting:check')")
    @OperLog(title = "销售齐套", businessType = 4)
    @PutMapping("/check/{id}")
    public R<Integer> check(@PathVariable Long id) {
        return kittingService.runKittingCheck(id) ? R.ok(1) : R.fail("齐套检查失败");
    }

    @Operation(summary = "生成出库单(齐套完成→WMS出库)")
    @PreAuthorize("@ps.hasPermi('sal:kitting:stockout')")
    @OperLog(title = "销售齐套", businessType = 4)
    @PutMapping("/stockout/{id}")
    public R<Long> stockOut(@PathVariable Long id) {
        return R.ok(kittingService.generateStockOut(id));
    }

    @Operation(summary = "生成请购单(缺料→SCM请购,按当日草稿追加) [PUT]")
    @PreAuthorize("@ps.hasPermi('sal:kitting:requisition')")
    @OperLog(title = "销售齐套", businessType = 4)
    @PutMapping("/requisition/{id}")
    public R<Long> requisition(@PathVariable Long id) {
        return R.ok(kittingService.generatePurchaseRequisition(id));
    }

    @Operation(summary = "T5-1 生成请购单(缺料→追加当日本地草稿) [POST]")
    @PreAuthorize("@ps.hasPermi('sal:kitting:requisition')")
    @OperLog(title = "销售齐套", businessType = 4)
    @PostMapping("/{kittingId}/generate-requisition")
    public R<Long> generateRequisition(@PathVariable Long kittingId) {
        return R.ok(kittingService.generatePurchaseRequisition(kittingId));
    }

    @Operation(summary = "取消齐套单")
    @PreAuthorize("@ps.hasPermi('sal:kitting:edit')")
    @OperLog(title = "销售齐套", businessType = 4)
    @PutMapping("/cancel/{id}")
    public R<Integer> cancel(@PathVariable Long id) {
        return kittingService.cancelKitting(id) ? R.ok(1) : R.fail("取消失败");
    }

    // ============ P0: 从订单行创建 ============

    @Operation(summary = "从销售订单行创建齐套单(自动匹配BOM+执行检查)")
    @PreAuthorize("@ps.hasPermi('sal:kitting:fromOrder')")
    @OperLog(title = "销售齐套", businessType = 1)
    @PostMapping("/from-order-line")
    public R<SalKitting> fromOrderLine(@RequestBody Map<String, Object> req) {
        Object sol = req.get("soLineId");
        if (sol == null) return R.fail("soLineId 必填");
        Long soLineId = Long.valueOf(String.valueOf(sol));
        Long bomId = null;
        if (req.get("bomId") != null && !String.valueOf(req.get("bomId")).isBlank()) {
            bomId = Long.valueOf(String.valueOf(req.get("bomId")));
        }
        return R.ok(kittingService.createFromOrderLine(soLineId, bomId));
    }

    // ============ 明细查询 ============

    @Operation(summary = "齐套明细列表")
    @PreAuthorize("@ps.hasPermi('sal:kitting:list')")
    @GetMapping("/{id}/lines")
    public R<List<SalKittingLine>> lines(@PathVariable Long id) {
        return R.ok(lineService.listByKittingId(id));
    }
}
