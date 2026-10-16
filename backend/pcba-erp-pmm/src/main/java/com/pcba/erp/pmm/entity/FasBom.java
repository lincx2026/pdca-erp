package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fas_bom")
public class FasBom implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long bomId;
    @NotNull(message = "成品不能为空")
    private Long productId;
    @NotNull(message = "子件不能为空")
    private Long componentId;
    @Size(max = 200)
    private String componentName;
    @Size(max = 20)
    private String componentType; // PCBA/STRUCTURE/DISPLAY/CABLE/FASTENER/PACKAGING/OTHER
    private BigDecimal qtyPer;
    @Size(max = 20)
    private String unit;
    private Integer needFeedCheck; // 1是 0否
    private Integer feedSequence;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @Size(max = 500)
    private String remark;
}
