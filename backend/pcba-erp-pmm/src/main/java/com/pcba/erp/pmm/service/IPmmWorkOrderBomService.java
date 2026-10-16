package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.PmmWorkOrderBom;

import java.util.List;

public interface IPmmWorkOrderBomService {
    IPage<PmmWorkOrderBom> selectPage(int pageNum, int pageSize, Long workOrderId, String materialCode, String materialName);
    PmmWorkOrderBom getDetail(Long id);
    Long save(PmmWorkOrderBom entity);
    boolean update(PmmWorkOrderBom entity);
    boolean remove(Long id);
    List<PmmWorkOrderBom> listByWorkOrder(Long workOrderId);
}
