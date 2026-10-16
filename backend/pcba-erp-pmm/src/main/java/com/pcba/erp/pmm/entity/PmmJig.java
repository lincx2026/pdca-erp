package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工装治具档案主表 pmm_jig
 */
@Data
@TableName("pmm_jig")
public class PmmJig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long jigId;

    @NotBlank(message = "工装编号不能为空")
    @Size(max = 50, message = "工装编号长度不能超过50")
    private String jigNo;

    @NotBlank(message = "工装名称不能为空")
    @Size(max = 150, message = "工装名称长度不能超过150")
    private String jigName;

    /**
     * STENCIL/CARRIER/FCT_FIXTURE/ICT_FIXTURE/REFLOW_FIXTURE/ROUTER_FIXTURE/TOOL/OTHER
     */
    @NotBlank(message = "工装分类不能为空")
    @Size(max = 30, message = "分类编码长度不能超过30")
    private String jigType;

    /** 关联产品机种 */
    private Long productId;
    @Size(max = 50) private String productCode;
    @Size(max = 150) private String productName;

    @Size(max = 255) private String specification;
    @Size(max = 50) private String material;
    private BigDecimal thicknessMm;
    private Integer holeCount;
    @Size(max = 50) private String sizeMm;
    private BigDecimal tensionNewton;

    /** 0=闲置/1=在用/2=借出/3=校准中/4=修磨中/5=维修中/6=报废 */
    @NotNull(message = "状态不能为空")
    private Integer status;

    @NotNull(message = "寿命次数不能为空")
    private Integer lifeCount;

    private Integer usedCount;

    private Integer qtyCurrent;
    private Integer qtyAvailable;
    private Integer qtyBorrowed;

    @Size(max = 50) private String warehouse;
    @Size(max = 50) private String location;

    @Size(max = 100) private String maker;
    private Long vendorId;
    @Size(max = 150) private String vendorName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate manufactureDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate receiveDate;

    private BigDecimal costAmount;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastCalDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate nextCalDate;
    private Integer calCycleDays;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long keeperId;
    @Size(max = 50)
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String keeperName;

    /** 归还后要清空为 null, 必须忽略 NOT_NULL 策略 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long borrowerId;
    @Size(max = 50)
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String borrowerName;

    @Size(max = 500) private String attachmentUrl;
    @Size(max = 500) private String remark;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
