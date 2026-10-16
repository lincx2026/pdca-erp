package com.pcba.erp.assembly.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcba.erp.assembly.entity.AsmSn;
import com.pcba.erp.assembly.entity.AsmSnNode;
import com.pcba.erp.assembly.mapper.AsmSnMapper;
import com.pcba.erp.assembly.mapper.AsmSnNodeMapper;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.exception.ServiceException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 工序节点录入 Controller (对接 MES 扫码)
 * - 接收批量 JSON 数组, 写入 asm_sn_node 并更新 asm_sn.currentProcess/currentStatus
 */
@Tag(name = "MES工序节点录入")
@RestController
@RequestMapping("/asm/node")
@RequiredArgsConstructor
public class AsmNodeController {

    private final AsmSnMapper snMapper;
    private final AsmSnNodeMapper snNodeMapper;

    /**
     * 原材料入库采集 INBOUND
     */
    @Operation(summary = "原材料入库采集")
    @PreAuthorize("@ps.hasPermi('asm:node:inbound')")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/inbound")
    public R<Integer> inbound(@RequestBody List<AsmSnNode> nodes) {
        return R.ok(processNodes(nodes, "INBOUND", 0, "待生产"));
    }

    /**
     * Feeder 上料采集 LOAD
     */
    @Operation(summary = "Feeder上料采集")
    @PreAuthorize("@ps.hasPermi('asm:node:load')")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/load")
    public R<Integer> load(@RequestBody List<AsmSnNode> nodes) {
        return R.ok(processNodes(nodes, "LOAD", 1, "生产中"));
    }

    /**
     * SMT 贴片采集
     */
    @Operation(summary = "SMT贴片采集")
    @PreAuthorize("@ps.hasPermi('asm:node:smt')")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/smt")
    public R<Integer> smt(@RequestBody List<AsmSnNode> nodes) {
        return R.ok(processNodes(nodes, "SMT", 2, "在制"));
    }

    /**
     * AOI 检测结果
     */
    @Operation(summary = "AOI检测结果录入")
    @PreAuthorize("@ps.hasPermi('asm:node:aoi')")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/aoi")
    public R<Integer> aoi(@RequestBody List<AsmSnNode> nodes) {
        int count = 0;
        for (AsmSnNode n : nodes) {
            n.setNodeType("AOI");
            count += upsertNode(n);
            // OK → 在制, NG → 不良, REWORK → 在制
            if (n.getSnCode() != null) {
                AsmSn sn = requireSn(n.getSnCode());
                if ("NG".equals(n.getNodeResult()) || "FAIL".equals(n.getNodeResult())) {
                    sn.setCurrentStatus(4); // 不良
                } else if ("REWORK".equals(n.getNodeResult())) {
                    sn.setCurrentStatus(4); // 不良待返工
                } else {
                    sn.setCurrentStatus(2); // 在制
                }
                sn.setCurrentProcess(n.getProcessCode());
                sn.setLastSeenTime(LocalDateTime.now());
                snMapper.updateById(sn);
            }
        }
        return R.ok(count);
    }

    /**
     * ICT 测试结果
     */
    @Operation(summary = "ICT测试结果录入")
    @PreAuthorize("@ps.hasPermi('asm:node:ict')")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/ict")
    public R<Integer> ict(@RequestBody List<AsmSnNode> nodes) {
        int count = 0;
        for (AsmSnNode n : nodes) {
            n.setNodeType("ICT");
            count += upsertNode(n);
            if (n.getSnCode() != null) {
                AsmSn sn = requireSn(n.getSnCode());
                if ("NG".equals(n.getNodeResult()) || "FAIL".equals(n.getNodeResult())) {
                    sn.setCurrentStatus(4); // 不良
                } else if ("REWORK".equals(n.getNodeResult())) {
                    sn.setCurrentStatus(4);
                } else {
                    sn.setCurrentStatus(2); // 在制
                }
                sn.setCurrentProcess(n.getProcessCode());
                sn.setLastSeenTime(LocalDateTime.now());
                snMapper.updateById(sn);
            }
        }
        return R.ok(count);
    }

    /**
     * 成品出货采集 OUTBOUND
     */
    @Operation(summary = "成品出货采集")
    @PreAuthorize("@ps.hasPermi('asm:node:outbound')")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/outbound")
    public R<Integer> outbound(@RequestBody List<AsmSnNode> nodes) {
        return R.ok(processNodes(nodes, "OUTBOUND", 6, "已出货"));
    }

    // ============ 通用处理 ============

    /**
     * 通用节点处理: 写入节点 + 更新 SN 当前状态
     * @param defaultNodeType 默认节点类型
     * @param newStatus 更新的 SN.currentStatus
     * @param processNameDesc 工序描述
     */
    private int processNodes(List<AsmSnNode> nodes, String defaultNodeType,
                             Integer newStatus, String processNameDesc) {
        int count = 0;
        for (AsmSnNode n : nodes) {
            if (n.getNodeType() == null || n.getNodeType().isEmpty()) {
                n.setNodeType(defaultNodeType);
            }
            if (n.getProcessName() == null || n.getProcessName().isEmpty()) {
                n.setProcessName(processNameDesc);
            }
            count += upsertNode(n);
            if (n.getSnCode() != null) {
                AsmSn sn = requireSn(n.getSnCode());
                sn.setCurrentStatus(newStatus);
                sn.setCurrentProcess(n.getProcessCode());
                sn.setLastSeenTime(LocalDateTime.now());
                snMapper.updateById(sn);
            }
        }
        return count;
    }

    /**
     * 根据 snCode 查/建 Sn
     */
    private AsmSn requireSn(String snCode) {
        AsmSn sn = snMapper.selectOne(
                new LambdaQueryWrapper<AsmSn>().eq(AsmSn::getSnCode, snCode).last("LIMIT 1"));
        if (sn == null) {
            throw new ServiceException("SN编码【" + snCode + "】不存在, 请先创建");
        }
        return sn;
    }

    /**
     * 写入单个节点 (自动分配 seqNo, 补默认时间)
     */
    private int upsertNode(AsmSnNode node) {
        if (node.getNodeTime() == null) node.setNodeTime(LocalDateTime.now());
        if (node.getSeqNo() == null && node.getSnId() != null) {
            // 自动分配 seqNo
            AsmSnNode last = snNodeMapper.selectOne(
                    new LambdaQueryWrapper<AsmSnNode>()
                            .eq(AsmSnNode::getSnId, node.getSnId())
                            .orderByDesc(AsmSnNode::getSeqNo)
                            .last("LIMIT 1"));
            node.setSeqNo(last == null ? 1 : (last.getSeqNo() == null ? 1 : last.getSeqNo() + 1));
        }
        if (node.getSnId() == null && node.getSnCode() != null) {
            // 补 snId
            AsmSn sn = snMapper.selectOne(
                    new LambdaQueryWrapper<AsmSn>().eq(AsmSn::getSnCode, node.getSnCode()).last("LIMIT 1"));
            if (sn != null) node.setSnId(sn.getSnId());
        }
        return snNodeMapper.insert(node);
    }
}
