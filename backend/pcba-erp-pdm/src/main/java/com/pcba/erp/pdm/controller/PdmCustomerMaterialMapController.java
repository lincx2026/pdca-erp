package com.pcba.erp.pdm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pdm.entity.PdmCustomerMaterialMap;
import com.pcba.erp.pdm.service.IPdmCustomerMaterialMapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户料号映射 Controller
 * - CRUD + 反查 + 启停 + 批量导入
 */
@Tag(name = "客户料号映射")
@RestController
@RequestMapping("/pdm/custmap")
@RequiredArgsConstructor
public class PdmCustomerMaterialMapController {

    private final IPdmCustomerMaterialMapService custMapService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** 客户下拉列表（供新增/编辑下拉选择） */
    @Operation(summary = "客户下拉列表")
    @PreAuthorize("@ps.hasPermi('pdm:custmap:list')")
    @GetMapping("/customer-options")
    public R<List<java.util.Map<String, Object>>> customerOptions() {
        List<java.util.Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT customer_id, customer_code, customer_name FROM scm_customer WHERE status = 1 ORDER BY customer_id");
        return R.ok(list);
    }

    /** 物料下拉列表（仅启用状态的成品/半成品） */
    @Operation(summary = "物料下拉列表")
    @PreAuthorize("@ps.hasPermi('pdm:custmap:list')")
    @GetMapping("/material-options")
    public R<List<java.util.Map<String, Object>>> materialOptions(@RequestParam(required = false) String keyword) {
        String sql = "SELECT material_id, material_code, material_name, specification " +
                     "FROM pdm_material WHERE status = 2";
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql += " AND (material_code LIKE ? OR material_name LIKE ?)";
            String kw = "%" + keyword + "%";
            params.add(kw);
            params.add(kw);
        }
        sql += " ORDER BY material_id LIMIT 200";
        List<java.util.Map<String, Object>> list = jdbcTemplate.queryForList(sql, params.toArray());
        return R.ok(list);
    }

    /* ============== 列表/详情 ============== */

    @Operation(summary = "客户料号分页查询")
    @PreAuthorize("@ps.hasPermi('pdm:custmap:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                  @RequestParam(defaultValue = "20") int pageSize,
                                  @RequestParam(required = false) Long customerId,
                                  @RequestParam(required = false) String customerName,
                                  @RequestParam(required = false) Long materialId,
                                  @RequestParam(required = false) String customerMaterialNo,
                                  @RequestParam(required = false) String materialCode,
                                  @RequestParam(required = false) Integer status) {
        IPage<PdmCustomerMaterialMap> page = custMapService.selectMapPage(pageNum, pageSize,
                customerId, customerName, materialId, customerMaterialNo, materialCode, status);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "客户料号映射详情")
    @PreAuthorize("@ps.hasPermi('pdm:custmap:list')")
    @GetMapping("/{id}")
    public R<PdmCustomerMaterialMap> get(@PathVariable Long id) {
        return R.ok(custMapService.getDetail(id));
    }

    @Operation(summary = "按客户料号反查工厂物料")
    @PreAuthorize("@ps.hasPermi('pdm:custmap:list')")
    @GetMapping("/reverse-lookup")
    public R<PdmCustomerMaterialMap> reverseLookup(@RequestParam String customerMaterialNo) {
        return R.ok(custMapService.reverseLookup(customerMaterialNo));
    }

    /* ============== CRUD ============== */

    @Operation(summary = "新增客户料号映射")
    @PreAuthorize("@ps.hasPermi('pdm:custmap:add')")
    @OperLog(title = "客户料号", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody PdmCustomerMaterialMap map) {
        Long id = custMapService.saveMap(map);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改客户料号映射")
    @PreAuthorize("@ps.hasPermi('pdm:custmap:edit')")
    @OperLog(title = "客户料号", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody PdmCustomerMaterialMap map) {
        return custMapService.updateMap(map) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除客户料号映射")
    @PreAuthorize("@ps.hasPermi('pdm:custmap:remove')")
    @OperLog(title = "客户料号", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return custMapService.removeMap(id) ? R.ok(1) : R.fail("删除失败");
    }

    /* ============== 启用/停用 ============== */

    @Operation(summary = "启用")
    @PreAuthorize("@ps.hasPermi('pdm:custmap:edit')")
    @PutMapping("/enable/{id}")
    public R<Integer> enable(@PathVariable Long id) {
        return custMapService.toggleStatus(id, 1) ? R.ok(1) : R.fail("启用失败");
    }

    @Operation(summary = "停用")
    @PreAuthorize("@ps.hasPermi('pdm:custmap:edit')")
    @PutMapping("/disable/{id}")
    public R<Integer> disable(@PathVariable Long id) {
        return custMapService.toggleStatus(id, 0) ? R.ok(1) : R.fail("停用失败");
    }

    /* ============== 批量导入 ============== */

    @Operation(summary = "批量导入（JSON数组）")
    @PreAuthorize("@ps.hasPermi('pdm:custmap:import')")
    @OperLog(title = "客户料号-导入", businessType = 5)
    @PostMapping("/batch-import")
    public R<String> batchImport(@RequestBody List<PdmCustomerMaterialMap> list,
                                  @RequestParam(defaultValue = "false") boolean updateSupport) {
        return R.ok(custMapService.batchImport(list, updateSupport));
    }
}
