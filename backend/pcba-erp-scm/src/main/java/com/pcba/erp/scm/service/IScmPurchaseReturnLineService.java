package com.pcba.erp.scm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmPurchaseReturnLine;

import java.util.List;

public interface IScmPurchaseReturnLineService {
    IPage<ScmPurchaseReturnLine> selectPage(int pageNum, int pageSize, Long returnId, String materialCode, String materialName);
    ScmPurchaseReturnLine getDetail(Long id);
    Long save(ScmPurchaseReturnLine entity);
    boolean update(ScmPurchaseReturnLine entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<ScmPurchaseReturnLine> options();
}
