package com.pcba.erp.assembly.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.assembly.entity.AsmSn;
import com.pcba.erp.assembly.entity.AsmSnNode;
import com.pcba.erp.assembly.entity.AsmSnRelation;
import com.pcba.erp.assembly.mapper.AsmSnNodeMapper;
import com.pcba.erp.assembly.mapper.AsmSnRelationMapper;
import com.pcba.erp.assembly.service.IAsmSnService;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SN 主档 Controller
 */
@Tag(name = "SN主档管理")
@RestController
@RequestMapping("/asm/sn")
@RequiredArgsConstructor
public class AsmSnController {

    private final IAsmSnService snService;
    private final AsmSnNodeMapper snNodeMapper;
    private final AsmSnRelationMapper snRelationMapper;

    @Operation(summary = "SN分页查询")
    @PreAuthorize("@ps.hasPermi('asm:sn:list')")
    @GetMapping("/list")
    public R<TableDataInfo> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String snCode,
            @RequestParam(required = false) String snType,
            @RequestParam(required = false) Integer snLevel,
            @RequestParam(required = false) Integer currentStatus,
            @RequestParam(required = false) Long workOrderId,
            @RequestParam(required = false) Long materialId) {
        IPage<AsmSn> page = snService.selectPage(pageNum, pageSize,
                snCode, snType, snLevel, currentStatus, workOrderId, materialId);
        TableDataInfo info = new TableDataInfo();
        info.setRows(page.getRecords());
        info.setTotal(page.getTotal());
        info.setCode(200);
        info.setMsg("查询成功");
        return R.ok(info);
    }

    @Operation(summary = "SN详情")
    @PreAuthorize("@ps.hasPermi('asm:sn:list')")
    @GetMapping("/{id}")
    public R<AsmSn> get(@PathVariable Long id) {
        return R.ok(snService.getDetail(id));
    }

    @Operation(summary = "SN下拉列表")
    @PreAuthorize("@ps.hasPermi('asm:sn:list')")
    @GetMapping("/options")
    public R<List<AsmSn>> options() {
        return R.ok(snService.options());
    }

    @Operation(summary = "生成SN编码")
    @PreAuthorize("@ps.hasPermi('asm:sn:add')")
    @GetMapping("/next-code")
    public R<String> nextCode() {
        return R.ok(snService.nextSnCode());
    }

    @Operation(summary = "新增SN")
    @PreAuthorize("@ps.hasPermi('asm:sn:add')")
    @OperLog(title = "SN主档", businessType = 1)
    @PostMapping
    public R<Long> add(@Validated @RequestBody AsmSn entity) {
        return R.ok(snService.save(entity));
    }

    @Operation(summary = "修改SN")
    @PreAuthorize("@ps.hasPermi('asm:sn:edit')")
    @OperLog(title = "SN主档", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody AsmSn entity) {
        return snService.update(entity) ? R.ok(1) : R.fail("修改失败");
    }

    @Operation(summary = "删除SN")
    @PreAuthorize("@ps.hasPermi('asm:sn:remove')")
    @OperLog(title = "SN主档", businessType = 3)
    @DeleteMapping("/{id}")
    public R<Integer> remove(@PathVariable Long id) {
        return snService.remove(id) ? R.ok(1) : R.fail("删除失败");
    }

    // ============== 状态 ==============

    @Operation(summary = "更新SN状态")
    @PreAuthorize("@ps.hasPermi('asm:sn:edit')")
    @OperLog(title = "SN主档", businessType = 4)
    @PutMapping("/update-status/{id}")
    public R<Integer> updateStatus(@PathVariable Long id,
                                   @RequestParam(required = false) Integer currentStatus,
                                   @RequestParam(required = false) String currentProcess) {
        return snService.updateStatus(id, currentStatus, currentProcess) ? R.ok(1) : R.fail("操作失败");
    }

    // ============== 追溯快捷 ==============

    @Operation(summary = "SN追溯(关联节点链+父子关系)")
    @PreAuthorize("@ps.hasPermi('asm:sn:list')")
    @GetMapping("/trace/{snId}")
    public R<List<AsmSnNode>> traceNodes(@PathVariable Long snId) {
        List<AsmSnNode> nodes = snNodeMapper.selectList(
                new LambdaQueryWrapper<AsmSnNode>()
                        .eq(AsmSnNode::getSnId, snId)
                        .orderByAsc(AsmSnNode::getSeqNo));
        return R.ok(nodes);
    }

    @Operation(summary = "SN关联父子关系")
    @PreAuthorize("@ps.hasPermi('asm:sn:list')")
    @GetMapping("/relations/{snId}")
    public R<List<AsmSnRelation>> relations(@PathVariable Long snId) {
        List<AsmSnRelation> list = snRelationMapper.selectList(
                new LambdaQueryWrapper<AsmSnRelation>()
                        .eq(AsmSnRelation::getParentSnId, snId)
                        .or()
                        .eq(AsmSnRelation::getChildSnId, snId));
        return R.ok(list);
    }
}
