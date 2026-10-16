package com.pcba.erp.scm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("scm_delivery_note")
public class ScmDeliveryNote implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long deliveryId;
    @Size(max=50) private String deliveryNo;
    private Long poId;
    @Size(max=50) private String poNo;
    /**
     * 采购大类（从关联采购单 biz_category 冗余过来）
     *  用于"生成入库草稿"按钮直接判断路由到哪个仓，无需 JOIN PO 再查。
     *   ELECTRONIC → 生成 WMS 电子料入库草稿
     *   ACCESSORY  → 生成 配件采购入库草稿 (acc-inbound, stock_type=ACC, purchase_no=deliveryNo)
     *   SPARE      → 暂未接入
     */
    @Size(max=20) private String bizCategory;
    @NotNull private Long supplierId;
    @Size(max=200) private String supplierName;
    @Size(max=100) private String waybillNo;
    @Size(max=100) private String carrierName;
    @Size(max=32) private String vehicleNo;
    @Size(max=64) private String driverName;
    @Size(max=32) private String driverPhone;
    private LocalDate deliveryDate;
    private Long warehouseId;
    private BigDecimal totalQty;
    private BigDecimal totalAmount;
    @Size(max=500) private String attachFile;
    /** 0=待收货 1=收货中 2=已收货 3=IQC检验中 4=入库完成 5=部分退货 9=取消 */
    private Integer status;
    private Long receiverId;
    private LocalDateTime receiveTime;
    @Size(max=64) private String iqcBy;
    private Integer iqcResult;
    @Size(max=500) private String iqcReport;
    private Long stockInId;
    @NotNull private Integer qcRequired = 0;
    @NotNull private Integer qcStatus = 0;
    @Size(max=64) private String qcInspector;
    private LocalDateTime qcInspectTime;
    @Size(max=1000) private String qcRemark;
    @Size(max=80) private String expressNo;
    @Size(max=80) private String batchNo;
    @Size(max=1000) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
