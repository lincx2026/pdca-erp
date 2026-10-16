package com.pcba.erp.sal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalArReceivableLine;
import java.util.List;

public interface ISalArReceivableLineService {
    IPage<SalArReceivableLine> selectPage(int pageNum, int pageSize, Long receivableId, String materialCode, String materialName);
    SalArReceivableLine getDetail(Long id);
    Long save(SalArReceivableLine entity);
    boolean update(SalArReceivableLine entity);
    boolean remove(Long id);
    List<SalArReceivableLine> listByReceivable(Long receivableId);
}
