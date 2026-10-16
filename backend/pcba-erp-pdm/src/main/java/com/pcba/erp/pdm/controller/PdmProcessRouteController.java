package com.pcba.erp.pdm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.DataScope;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pdm.entity.PdmProcessRoute;
import com.pcba.erp.pdm.entity.PdmProcessStep;
import com.pcba.erp.pdm.service.IPdmProcessRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "工艺路线管理")
@RestController
@RequestMapping("/pdm/process-route")
@RequiredArgsConstructor
public class PdmProcessRouteController {

    private final IPdmProcessRouteService routeService;

    /* ============== 列表/详情 ============== */

    @Operation(summary = "工艺路线分页查询")
    @PreAuthorize("@ps.hasPermi('pdm:process:list')")
    @DataScope(deptField = "", userField = "r.create_by")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String routeCode,
                                 @RequestParam(required = false) String routeName,
                                 @RequestParam(required = false) Long productId,
                                 @RequestParam(required = false) String productCode,
                                 @RequestParam(required = false) Integer status) {
        IPage<PdmProcessRoute> page = routeService.selectRoutePage(pageNum, pageSize,
                routeCode, routeName, productId, productCode, status);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "工艺路线详情（含步骤 + 汇总字段）")
    @PreAuthorize("@ps.hasPermi('pdm:process:list')")
    @GetMapping("/{id}")
    public R<PdmProcessRoute> get(@PathVariable Long id) {
        return R.ok(routeService.getDetail(id));
    }

    @Operation(summary = "生成新工艺路线编码")
    @PreAuthorize("@ps.hasPermi('pdm:process:add')")
    @GetMapping("/next-code")
    public R<String> nextCode() { return R.ok(routeService.nextRouteCode()); }

    @Operation(summary = "根据产品查默认启用工艺路线")
    @PreAuthorize("@ps.hasPermi('pdm:process:list')")
    @GetMapping("/default")
    public R<PdmProcessRoute> getDefault(@RequestParam Long productId) {
        return R.ok(routeService.getDefaultRoute(productId));
    }

    /* ============== CRUD ============== */

    @Operation(summary = "新增工艺路线（主档+步骤一次性保存, 返回ID）")
    @PreAuthorize("@ps.hasPermi('pdm:process:add')")
    @OperLog(title = "工艺路线管理", businessType = 1)
    @PostMapping
    public R<Long> add(@RequestBody PdmProcessRoute route) {
        Long id = routeService.saveWithSteps(route);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改工艺路线主档")
    @PreAuthorize("@ps.hasPermi('pdm:process:edit')")
    @OperLog(title = "工艺路线管理", businessType = 2)
    @PutMapping
    public R<Integer> edit(@RequestBody PdmProcessRoute route) {
        return routeService.update(route) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除工艺路线（级联步骤）")
    @PreAuthorize("@ps.hasPermi('pdm:process:remove')")
    @OperLog(title = "工艺路线管理", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return routeService.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "全量覆盖工艺步骤")
    @PreAuthorize("@ps.hasPermi('pdm:process:edit')")
    @OperLog(title = "工艺路线-步骤", businessType = 2)
    @PutMapping("/replace-steps/{routeId}")
    public R<Integer> replaceSteps(@PathVariable Long routeId,
                                    @RequestBody(required = false) List<PdmProcessStep> steps) {
        return routeService.replaceSteps(routeId, steps) ? R.ok(1) : R.fail("保存失败");
    }

    /* ============== 状态/默认 ============== */

    @Operation(summary = "设为默认工艺路线")
    @PreAuthorize("@ps.hasPermi('pdm:process:edit')")
    @PutMapping("/set-default/{id}")
    public R<Integer> setDefault(@PathVariable Long id) {
        return routeService.setDefault(id) ? R.ok(1) : R.fail("设置失败");
    }

    @Operation(summary = "启用工艺路线")
    @PreAuthorize("@ps.hasPermi('pdm:process:edit')")
    @PutMapping("/enable/{id}")
    public R<Integer> enable(@PathVariable Long id) {
        return routeService.enable(id) ? R.ok(1) : R.fail("启用失败");
    }

    @Operation(summary = "停用工艺路线")
    @PreAuthorize("@ps.hasPermi('pdm:process:edit')")
    @PutMapping("/disable/{id}")
    public R<Integer> disable(@PathVariable Long id) {
        return routeService.disable(id) ? R.ok(1) : R.fail("停用失败");
    }

    /* ============== 复制 ============== */

    @Operation(summary = "复制工艺路线（含步骤, 默认停用, 新编码）")
    @PreAuthorize("@ps.hasPermi('pdm:process:add')")
    @OperLog(title = "工艺路线-复制", businessType = 1)
    @PostMapping("/copy/{sourceId}")
    public R<Long> copy(@PathVariable Long sourceId,
                        @RequestParam(required = false) String newRouteName) {
        Long id = routeService.copy(sourceId, newRouteName);
        return id == null ? R.fail("复制失败") : R.ok(id);
    }
}
