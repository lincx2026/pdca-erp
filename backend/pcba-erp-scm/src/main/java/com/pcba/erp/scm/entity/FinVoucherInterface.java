package com.pcba.erp.scm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fin_voucher_interface")
public class FinVoucherInterface implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO) private Long voucherId;
    @Size(max=50) private String voucherNo;
    @Size(max=20) private String voucherType;
    @Size(max=30) private String bizSource;
    private Long bizId;
    @Size(max=50) private String bizNo;
    @NotNull private LocalDate voucherDate;
    @Size(max=20) private String companyCode;
    @Size(max=10) private String currency;
    private BigDecimal exchangeRate;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private BigDecimal totalDebitCny;
    private BigDecimal totalCreditCny;
    /** JSON 行项数组 [{lineNo,subjectCode,subjectName,debit,credit,auxType,auxCode,auxName,remark}] */
    private String voucherLines;
    /** 0待推 1推中 2成功 3失败 4已确认 9作废 */
    private Integer pushStatus;
    private LocalDateTime pushTime;
    @Size(max=1000) private String pushMsg;
    @Size(max=100) private String externalVoucherNo;
    @Size(max=500) private String attachment;
    @Size(max=1000) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
