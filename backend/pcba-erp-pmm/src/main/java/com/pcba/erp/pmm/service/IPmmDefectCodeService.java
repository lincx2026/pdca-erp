package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.PmmDefectCode;

import java.util.List;

public interface IPmmDefectCodeService {
    IPage<PmmDefectCode> selectPage(int pageNum, int pageSize, String defectCode, String defectName,
                                    String defectCategory, Integer status);
    PmmDefectCode getDetail(Long id);
    Long save(PmmDefectCode entity);
    boolean update(PmmDefectCode entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<PmmDefectCode> options();
}
