package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("fas_step")
public class FasStep implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long stepId;
    @NotNull(message = "工单不能为空")
    private Long orderId;
    @NotNull(message = "工序号不能为空")
    private Integer stepNo;
    @NotBlank(message = "工位名称不能为空")
    @Size(max = 100)
    private String stepName;
    @Size(max = 100)
    private String workcenter;
    private Integer stdTimeMin;
    private Integer status; // 0待开始 1进行中 2已完成 3跳过
    @Size(max = 50)
    private String operator;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    private Integer qtyPass;
    private Integer qtyNg;
    @Size(max = 500)
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
