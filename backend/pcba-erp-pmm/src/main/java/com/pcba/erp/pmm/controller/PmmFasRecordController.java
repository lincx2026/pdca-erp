package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.FasRecord;
import com.pcba.erp.pmm.service.IPmmFasRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "后道组装记录管理")
@RestController
@RequestMapping("/fas/record")
@RequiredArgsConstructor
public class PmmFasRecordController {

    private final IPmmFasRecordService service;

    @Operation(summary = "记录分页查询")
    @PreAuthorize("@ps.hasPermi('pm:fas:record:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) Long orderId,
                                 @RequestParam(required = false) String recordType,
                                 @RequestParam(required = false) String operator) {
        IPage<FasRecord> page = service.selectPage(pageNum, pageSize, orderId, recordType, operator);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "记录详情")
    @PreAuthorize("@ps.hasPermi('pm:fas:record:query')")
    @GetMapping("/{id}")
    public R<FasRecord> get(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "按工单查询记录列表")
    @PreAuthorize("@ps.hasPermi('pm:fas:record:list')")
    @GetMapping("/order/{orderId}")
    public R<List<FasRecord>> listByOrder(@PathVariable Long orderId) {
        return R.ok(service.listByOrder(orderId));
    }

    @Operation(summary = "新增记录")
    @PreAuthorize("@ps.hasPermi('pm:fas:record:list')")
    @OperLog(title = "后道组装记录", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody FasRecord entity) {
        Long id = service.create(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "删除记录")
    @PreAuthorize("@ps.hasPermi('pm:fas:record:list')")
    @OperLog(title = "后道组装记录", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return R.ok();
    }
}
