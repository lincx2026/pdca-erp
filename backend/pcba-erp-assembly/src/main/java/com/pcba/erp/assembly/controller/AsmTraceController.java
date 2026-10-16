package com.pcba.erp.assembly.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcba.erp.assembly.entity.AsmSn;
import com.pcba.erp.assembly.entity.AsmSnNode;
import com.pcba.erp.assembly.entity.AsmSnRelation;
import com.pcba.erp.assembly.entity.AsmFeederLoad;
import com.pcba.erp.assembly.mapper.AsmSnMapper;
import com.pcba.erp.assembly.mapper.AsmSnNodeMapper;
import com.pcba.erp.assembly.mapper.AsmSnRelationMapper;
import com.pcba.erp.assembly.mapper.AsmFeederLoadMapper;
import com.pcba.erp.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 追溯专用 Controller (纯查询, 不做写操作)
 */
@Tag(name = "SN追溯查询")
@RestController
@RequestMapping("/asm/trace")
@RequiredArgsConstructor
public class AsmTraceController {

    private final AsmSnMapper snMapper;
    private final AsmSnNodeMapper snNodeMapper;
    private final AsmSnRelationMapper snRelationMapper;
    private final AsmFeederLoadMapper feederLoadMapper;

    /**
     * 正向追溯: 成品SN → 所有原材料批次SN
     */
    @Operation(summary = "正向追溯(成品→原材料批次)")
    @PreAuthorize("@ps.hasPermi('asm:trace:list')")
    @GetMapping("/upstream/{snCode}")
    public R<List<Map<String, Object>>> upstream(@PathVariable String snCode) {
        AsmSn sn = snMapper.selectOne(
                new LambdaQueryWrapper<AsmSn>().eq(AsmSn::getSnCode, snCode).last("LIMIT 1"));
        if (sn == null) return R.ok(new ArrayList<>());

        List<Map<String, Object>> result = new ArrayList<>();
        // 1. 查直接子级
        List<AsmSnRelation> direct = snRelationMapper.selectList(
                new LambdaQueryWrapper<AsmSnRelation>().eq(AsmSnRelation::getParentSnId, sn.getSnId()));
        for (AsmSnRelation rel : direct) {
            AsmSn child = snMapper.selectById(rel.getChildSnId());
            if (child != null) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("relationType", rel.getRelationType());
                row.put("bomLineId", rel.getBomLineId());
                row.put("childQty", rel.getChildQty());
                row.put("childSn", child);
                result.add(row);
            }
        }
        return R.ok(result);
    }

    /**
     * 反向追溯: 原材料SN → 所有用了它的成品SN
     */
    @Operation(summary = "反向追溯(原材料→所有成品)")
    @PreAuthorize("@ps.hasPermi('asm:trace:list')")
    @GetMapping("/downstream/{snCode}")
    public R<List<Map<String, Object>>> downstream(@PathVariable String snCode) {
        AsmSn sn = snMapper.selectOne(
                new LambdaQueryWrapper<AsmSn>().eq(AsmSn::getSnCode, snCode).last("LIMIT 1"));
        if (sn == null) return R.ok(new ArrayList<>());

        List<Map<String, Object>> result = new ArrayList<>();
        List<AsmSnRelation> used = snRelationMapper.selectList(
                new LambdaQueryWrapper<AsmSnRelation>().eq(AsmSnRelation::getChildSnId, sn.getSnId()));
        for (AsmSnRelation rel : used) {
            AsmSn parent = snMapper.selectById(rel.getParentSnId());
            if (parent != null) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("relationType", rel.getRelationType());
                row.put("workOrderId", rel.getWorkOrderId());
                row.put("parentSn", parent);
                result.add(row);
            }
        }
        return R.ok(result);
    }

    /**
     * 获取 SN 的全流程节点链
     */
    @Operation(summary = "获取SN全流程节点链")
    @PreAuthorize("@ps.hasPermi('asm:trace:list')")
    @GetMapping("/node/{snCode}")
    public R<List<AsmSnNode>> node(@PathVariable String snCode) {
        AsmSn sn = snMapper.selectOne(
                new LambdaQueryWrapper<AsmSn>().eq(AsmSn::getSnCode, snCode).last("LIMIT 1"));
        if (sn == null) return R.ok(new ArrayList<>());

        List<AsmSnNode> nodes = snNodeMapper.selectList(
                new LambdaQueryWrapper<AsmSnNode>()
                        .eq(AsmSnNode::getSnId, sn.getSnId())
                        .orderByAsc(AsmSnNode::getSeqNo));
        return R.ok(nodes);
    }

    /**
     * 查询某个物料/料盘在 Feeder 上料的历史
     */
    @Operation(summary = "查询物料/料盘的Feeder上料历史")
    @PreAuthorize("@ps.hasPermi('asm:trace:list')")
    @GetMapping("/feeder-log")
    public R<List<AsmFeederLoad>> feederLog(
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String reelSn,
            @RequestParam(required = false) String equipmentCode) {
        LambdaQueryWrapper<AsmFeederLoad> w = new LambdaQueryWrapper<>();
        if (materialId != null) w.eq(AsmFeederLoad::getMaterialId, materialId);
        if (reelSn != null && !reelSn.isEmpty()) w.eq(AsmFeederLoad::getReelSn, reelSn);
        if (equipmentCode != null && !equipmentCode.isEmpty()) w.like(AsmFeederLoad::getEquipmentCode, equipmentCode);
        w.orderByDesc(AsmFeederLoad::getLoadTime);
        return R.ok(feederLoadMapper.selectList(w));
    }
}
