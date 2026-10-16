package com.pcba.erp.scm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmApPayableLine;
import java.util.List;

public interface IScmApPayableLineService {
    IPage<ScmApPayableLine> selectPage(int pageNum, int pageSize, Long payableId, String materialCode, String materialName);
    ScmApPayableLine getDetail(Long id);
    Long save(ScmApPayableLine entity);
    boolean update(ScmApPayableLine entity);
    boolean remove(Long id);
    List<ScmApPayableLine> listByPayable(Long payableId);
}
