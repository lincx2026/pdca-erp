package com.pcba.erp.pdm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物料替代关系表
 */
@Data
@TableName("pdm_material_alternate")
public class PdmMaterialAlternate implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 替代组号 ALT-yyMMddHHmmss */
    private String groupId;

    /** 主料 (被替代) */
    private Long mainMaterialId;

    /** 替代料 (代替主料) */
    private Long altMaterialId;

    /** 1=完全互替 2=单向 3=条件(BOM行限定) */
    private Integer altType;

    /** 优先级 1 最高 */
    private Integer priority;

    /** 替代比例 1主料=X替代料 */
    private BigDecimal subRate;

    /** 0=草稿 1=审批中 2=生效 3=停用 4=驳回 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    private String auditBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;
    private String rejectReason;
    private String remark;

    @TableField(select = false)
    private Long deptId;

    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /* ================ 非 DB 字段，列表显示用 ================ */
    @TableField(exist = false)
    private String mainMaterialCode;
    @TableField(exist = false)
    private String mainMaterialName;
    @TableField(exist = false)
    private String altMaterialCode;
    @TableField(exist = false)
    private String altMaterialName;
}
