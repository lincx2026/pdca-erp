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
 * 设备档案主表 pmm_equipment
 */
@Data
@TableName("pmm_equipment")
public class PmmEquipment implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long equipmentId;

    @NotBlank(message = "设备编码不能为空")
    @Size(max = 50)
    private String equipmentNo;

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 200)
    private String equipmentName;

    /** PRINTER印刷机 / SPI锡膏检测 / PLACEMENT贴片机 / REFLOW回流焊 / AOI外观检测 / WAVE波峰焊 / ICT / FCT / XRAY / OTHER */
    @NotBlank(message = "设备类型不能为空")
    @Size(max = 30)
    private String equipmentType;

    @Size(max = 100)
    private String brand;

    @Size(max = 100)
    private String model;

    @Size(max = 100)
    private String serialNo;

    @Size(max = 50)
    private String assetNo;

    @Size(max = 100)
    private String workshop;

    @Size(max = 100)
    private String workCenter;

    @Size(max = 200)
    private String location;

    private Long supplierId;

    @Size(max = 200)
    private String supplierName;

    private LocalDate purchaseDate;
    private LocalDate warrantyEndDate;
    private LocalDate installDate;

    /** 0闲置 / 1在用 / 2保养中 / 3维修中 / 4报废 */
    @NotNull(message = "设备状态不能为空")
    private Integer status;

    /** 1关键 / 2一般 */
    @NotNull(message = "设备等级不能为空")
    private Integer equipLevel;

    @NotNull(message = "每小时产能不能为空")
    private BigDecimal capacityPerHour;

    @Size(max = 20)
    private String capacityUnit;

    @NotNull(message = "保养周期(天)不能为空")
    private Integer maintainCycleDays;

    private LocalDate lastMaintainDate;
    private LocalDate nextMaintainDate;

    @NotNull(message = "校准周期(天)不能为空, 无校准需求填0")
    private Integer calibrationCycleDays;

    private LocalDate lastCalDate;
    private LocalDate nextCalDate;

    @Size(max = 200)
    private String operatorIds;

    @Size(max = 500)
    private String operatorNames;

    @Size(max = 500)
    private String remark;

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
