package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fas_order")
public class FasOrder implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long orderId;
    @NotBlank(message = "工单号不能为空")
    @Size(max = 32)
    private String orderNo;
    @NotNull(message = "成品不能为空")
    private Long productId;
    @Size(max = 200)
    private String productName;
    @NotNull(message = "计划数量不能为空")
    private Integer qtyPlan;
    private Integer qtyComplete;
    private Integer qtyNg;
    private Integer status; // 0新建 1已下达 2进行中 3已完工 4已入库 5已关闭
    @Size(max = 100)
    private String lineName;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate planStartDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate planEndDate;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualEnd;
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
    @Size(max = 500)
    private String remark;
}
