package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.FasOrder;

public interface IPmmFasOrderService {
    IPage<FasOrder> selectPage(int pageNum, int pageSize, String orderNo, String productName,
                               Integer status, String lineName);
    FasOrder getById(Long id);
    Long create(FasOrder entity);
    void update(FasOrder entity);
    void remove(Long id);
    void release(Long id);
    void startWork(Long id);
    void complete(Long id);
    /** 生成完工入库单草稿(不再直接入库); 仓管在成品仓库中审核通过 */
    void stockIn(Long id, String warehouse, String batchNo, Integer qty, String remark);
    void close(Long id);
}
