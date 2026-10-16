package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fas_record")
@JsonIgnoreProperties(ignoreUnknown = true)
public class FasRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long recordId;
    private Long orderId;
    private Long stepId;
    @NotBlank(message = "记录类型不能为空")
    @Size(max = 20)
    private String recordType; // FEED/WORK/CHECK/NG
    @Size(max = 50)
    private String operator;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime;
    @Size(max = 50)
    private String batchNo;
    private Long componentId;
    private BigDecimal qtyUsed;
    @Size(max = 10)
    private String checkResult; // OK/NG
    @Size(max = 50)
    private String ngType;
    @Size(max = 500)
    private String ngDesc;
    private BigDecimal costAmount;
    @Size(max = 500)
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
