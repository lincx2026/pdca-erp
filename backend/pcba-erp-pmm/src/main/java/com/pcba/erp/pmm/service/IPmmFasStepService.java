package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.FasStep;

import java.util.List;

public interface IPmmFasStepService {
    IPage<FasStep> selectPage(int pageNum, int pageSize, Long orderId, String stepName, Integer status);
    List<FasStep> listByOrder(Long orderId);
    Long create(FasStep entity);
    void update(FasStep entity);
    void remove(Long id);
    void startStep(Long stepId, String operator);
    void completeStep(Long stepId, Integer qtyPass, Integer qtyNg, String operator);
}
