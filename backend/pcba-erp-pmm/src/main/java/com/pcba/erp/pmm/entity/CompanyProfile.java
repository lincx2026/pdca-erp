package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 公司档案 company_profile (单行表, profile_id 固定为 1)
 */
@Data
@TableName("company_profile")
public class CompanyProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键, 固定为 1 */
    @TableId(type = IdType.INPUT)
    private Long profileId;

    private String companyName;
    private String shortName;
    private String logoUrl;
    private String legalPerson;
    private String contactPhone;
    private String contactEmail;
    private String address;
    private String postalCode;
    private String taxNo;
    private String bankName;
    private String bankAccount;
    private String invoiceTitle;

    private BigDecimal taxRate;

    private String currency;
    private Integer decimalPlaces;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private String remark;
}
