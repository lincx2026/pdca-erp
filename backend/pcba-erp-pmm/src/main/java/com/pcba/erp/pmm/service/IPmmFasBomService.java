package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.FasBom;

import java.util.List;

public interface IPmmFasBomService {
    IPage<FasBom> selectPage(int pageNum, int pageSize, Long productId, String componentName, String componentType);
    FasBom getById(Long id);
    List<FasBom> listByProduct(Long productId);
    Long create(FasBom entity);
    void update(FasBom entity);
    void remove(Long id);
}
