package com.pcba.erp.scm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmPurchaseRequisitionLine;

import java.util.List;

public interface IScmPurchaseRequisitionLineService {
    IPage<ScmPurchaseRequisitionLine> selectPage(int pageNum, int pageSize, Long requisitionId, String materialCode, String materialName);
    ScmPurchaseRequisitionLine getDetail(Long id);
    ScmPurchaseRequisitionLine estimate(Long materialId, Long supplierId, java.math.BigDecimal qty);
    List<Long> saveBatch(List<ScmPurchaseRequisitionLine> lines);
    void refreshEstimates(Long requisitionId);
    Long save(ScmPurchaseRequisitionLine entity);
    boolean update(ScmPurchaseRequisitionLine entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<ScmPurchaseRequisitionLine> options();
}
