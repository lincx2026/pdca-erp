package com.pcba.erp.pmm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.pmm.entity.PmmKanbanCard;
import com.pcba.erp.pmm.service.IPmmKanbanCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "生产看板管理")
@RestController
@RequestMapping("/pm/kanban")
@RequiredArgsConstructor
public class PmmKanbanCardController {

    private final IPmmKanbanCardService service;

    @Operation(summary = "看板卡片分页列表")
    @PreAuthorize("@ps.hasPermi('pm:kanban:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) Long workOrderId,
                                 @RequestParam(required = false) Integer lineNo,
                                 @RequestParam(required = false) String workCenter,
                                 @RequestParam(required = false) Integer isFlagged,
                                 @RequestParam(required = false) String title) {
        IPage<PmmKanbanCard> page = service.selectPage(pageNum, pageSize, workOrderId, lineNo, workCenter, isFlagged, title);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "看板大屏视图: 返回所有卡片 (前端按 lineNo/columnNo 分组渲染)")
    @PreAuthorize("@ps.hasPermi('pm:kanban:list')")
    @GetMapping("/board")
    public R<List<PmmKanbanCard>> board() { return R.ok(service.allCards()); }

    @Operation(summary = "看板卡片详情")
    @PreAuthorize("@ps.hasPermi('pm:kanban:list')")
    @GetMapping("/{id}")
    public R<PmmKanbanCard> get(@PathVariable Long id) { return R.ok(service.getDetail(id)); }

    @Operation(summary = "新增看板卡片")
    @PreAuthorize("@ps.hasPermi('pm:kanban:list')")
    @OperLog(title = "生产看板", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody PmmKanbanCard entity) {
        Long id = service.save(entity);
        return id == null ? R.fail("新增失败") : R.ok(id);
    }

    @Operation(summary = "修改看板卡片")
    @PreAuthorize("@ps.hasPermi('pm:kanban:list')")
    @OperLog(title = "生产看板", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody PmmKanbanCard entity) {
        return service.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除看板卡片")
    @PreAuthorize("@ps.hasPermi('pm:kanban:list')")
    @OperLog(title = "生产看板", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return service.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    @Operation(summary = "拖拽移动卡片到指定列")
    @PreAuthorize("@ps.hasPermi('pm:kanban:list')")
    @OperLog(title = "生产看板", businessType = 2)
    @PutMapping("/move/{id}")
    public R<Integer> move(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return service.moveLine(id, body.get("lineNo"), body.get("columnNo")) ? R.ok(1) : R.fail("移动失败");
    }
}
