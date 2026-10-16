package com.pcba.erp.pmm.service;

import com.pcba.erp.pmm.entity.PmProjectCategory;

import java.util.List;

public interface IPmProjectCategoryService {

    List<PmProjectCategory> tree();

    List<PmProjectCategory> list();

    PmProjectCategory getDetail(Long id);

    Long save(PmProjectCategory entity);

    boolean update(PmProjectCategory entity);

    boolean remove(Long id);
}
