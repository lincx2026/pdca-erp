package com.pcba.erp.assembly.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SN 工序节点 asm_sn_node
 * - MES 每扫一次 SN 写入一条节点记录
 * - 一个 SN 的完整流程链由多条节点按 seqNo 串起
 */
@Data
@TableName("asm_sn_node")
public class AsmSnNode implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long nodeId;

    /** 主 SN */
    private Long snId;
    @Size(max = 100)
    private String snCode;

    /** 同一 SN 上的节点顺序 */
    private Integer seqNo;

    /**
     * 节点类型
     * INBOUND / KITTING / LOAD / SMT / REFLOW / AOI / ICT /
     * FUNCTION / REWORK / PACKAGE / OUTBOUND
     */
    @Size(max = 30)
    private String nodeType;

    /** 工序编码/名称 */
    @Size(max = 50)
    private String processCode;
    @Size(max = 100)
    private String processName;

    /** 设备信息 */
    private Long equipmentId;
    @Size(max = 50)
    private String equipmentCode;
    @Size(max = 100)
    private String equipmentName;

    /** 程序名 (SMT/ICT 等带程序的工序) */
    @Size(max = 100)
    private String programName;

    /** Feeder 位号 (SMT 上料节点) */
    @Size(max = 20)
    private String feederNo;

    /** 操作人 */
    private Long operatorId;
    @Size(max = 64)
    private String operatorName;

    /**
     * 节点结果
     * OK / NG / REWORK / PASS / FAIL
     */
    @Size(max = 20)
    private String nodeResult;

    /** 不良代码/描述 (NG 场景) */
    @Size(max = 50)
    private String defectCode;
    @Size(max = 500)
    private String defectDesc;

    /** 节点采集时间 */
    private LocalDateTime nodeTime;

    @Size(max = 500)
    private String remark;

    /** 扩展 JSON: 可存 MES 原始报文 / 测试参数 / 波形摘要 等 */
    private String extData;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
