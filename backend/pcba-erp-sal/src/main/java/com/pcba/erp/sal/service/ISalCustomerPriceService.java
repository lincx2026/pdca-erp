package com.pcba.erp.sal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalCustomerPrice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ISalCustomerPriceService {

    IPage<SalCustomerPrice> selectPage(int pageNum, int pageSize, Long customerId, Long materialId,
                                       String currency, Integer status, String customerName, Integer onlyValid);

    SalCustomerPrice getDetail(Long id);

    /** 新增(默认status=0草稿); 同客户+物料同(valid_from~valid_to)区间查重 */
    Long save(SalCustomerPrice entity);

    /** 批量新增: 共享客户/币种/有效期, 每行独立物料+单价; 返回ID列表 */
    java.util.List<Long> batchSave(java.util.List<SalCustomerPrice> items);

    /** 修改(仅 0草稿/4驳回 可改) */
    boolean update(SalCustomerPrice entity);

    /** 删除(仅 0草稿/4驳回 可删) */
    boolean remove(Long id);

    /** 提交审核: 0草稿/4驳回 → 1待审 */
    boolean submit(Long id);

    /** 审核通过: 1待审 → 2已审(生效); 同客户+物料生效价中仅保留一个is_default=1 */
    boolean audit(Long id, String auditBy);

    /** 驳回: 1待审 → 4驳回(填驳回理由) */
    boolean reject(Long id, String reason, String auditBy);

    /** 停用: 2已审 → 3停用 */
    boolean disable(Long id);

    /** 启用(重新生效): 3停用 → 2已审 */
    boolean enable(Long id);

    /** 设为默认: 同客户+物料生效价(status=2)中仅本条is_default=1 */
    boolean setDefault(Long id);

    /** SO下单带价: 按 customer+material+qty+date 取最优生效价(is_default优先→min_qty最大→单价最低) */
    SalCustomerPrice matchPrice(Long customerId, Long materialId, BigDecimal qty, LocalDate date);

    /** 过期价自动流转: status=2 且 valid_to < 今天 → status=3(停用), 同时清掉 is_default */
    int expireOverduePrices();

    List<SalCustomerPrice> options();

    /**
     * P1: 批量把已审报价条目(客户价目表) 转成 一张销售订单
     * - 同一个 customerId
     * - 只接受 status=2(已审) 且 order_id 为空 的条目
     * - 生成 订单头 source_type='FROM_PRICE' + N条订单行
     * - 逐条回写 报价条目: status→5(已转单), order_id, order_line_id
     *
     * @param customerId       客户ID(必须一致)
     * @param itemList         条目列表: 每个 { priceId, qty(必填), deliveryDate(可选) }
     * @param extra            可选覆盖字段: deliveryDate/soDate/paymentTerm/remark/salesRepId/warehouseId
     * @return 新建销售订单ID (soId)
     */
    Long convertToSalesOrder(Long customerId,
                             java.util.List<java.util.Map<String, Object>> itemList,
                             java.util.Map<String, Object> extra);
}
