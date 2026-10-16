package com.pcba.erp.scm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmDeliveryNoteLine;

import java.util.List;

public interface IScmDeliveryNoteLineService {
    IPage<ScmDeliveryNoteLine> selectPage(int pageNum, int pageSize, Long deliveryId, String materialCode, String materialName);
    ScmDeliveryNoteLine getDetail(Long id);
    Long save(ScmDeliveryNoteLine entity);
    boolean update(ScmDeliveryNoteLine entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<ScmDeliveryNoteLine> options();
}
