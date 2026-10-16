package com.pcba.erp.scm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmPurchaseOrderLine;

import java.util.List;

public interface IScmPurchaseOrderLineService {
    IPage<ScmPurchaseOrderLine> selectPage(int pageNum, int pageSize, Long poId, String materialCode, String materialName);
    ScmPurchaseOrderLine getDetail(Long id);
    Long save(ScmPurchaseOrderLine entity);
    boolean update(ScmPurchaseOrderLine entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<ScmPurchaseOrderLine> options();
}
