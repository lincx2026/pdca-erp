package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.HrmEmployee;
import com.pcba.erp.pmm.service.IHrmEmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "员工管理")
@RestController
@RequestMapping("/hrm/employee")
@RequiredArgsConstructor
public class HrmEmployeeController {

    private final IHrmEmployeeService service;

    @Operation(summary = "员工分页列表")
    @PreAuthorize("@ps.hasPermi('hrm:employee:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) String employeeNo,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) String phone,
                                 @RequestParam(required = false) Long deptId,
                                 @RequestParam(required = false) Integer status) {
        IPage<HrmEmployee> page = service.selectPage(pageNum, pageSize,
                employeeNo, name, phone, deptId, status);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "部门树(供员工管理选择)")
    @PreAuthorize("@ps.hasPermi('hrm:employee:list')")
    @GetMapping("/dept-tree")
    public R<List<Map<String, Object>>> deptTree() {
        return R.ok(service.deptTree());
    }

    @Operation(summary = "员工详情")
    @PreAuthorize("@ps.hasPermi('hrm:employee:list')")
    @GetMapping("/{id}")
    public R<HrmEmployee> get(@PathVariable Long id) {
        return R.ok(service.getDetail(id));
    }

    @Operation(summary = "新增员工")
    @PreAuthorize("@ps.hasPermi('hrm:employee:save')")
    @OperLog(title = "员工管理", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody HrmEmployee entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改员工")
    @PreAuthorize("@ps.hasPermi('hrm:employee:save')")
    @OperLog(title = "员工管理", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody HrmEmployee entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除员工")
    @PreAuthorize("@ps.hasPermi('hrm:employee:remove')")
    @OperLog(title = "员工管理", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }
}
