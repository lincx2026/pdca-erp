package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.PmProject;

public interface IPmProjectService {

    IPage<PmProject> selectPage(int pageNum, int pageSize,
                                String projectCode, String projectName,
                                String customerName, String manager,
                                Long categoryId,
                                Integer status, String priority);

    PmProject getDetail(Long id);

    Long save(PmProject entity);

    boolean update(PmProject entity);

    boolean remove(Long id);

    /**
     * 状态流转: 0待启动->1进行中, 1进行中->2已完成, 0/1->3已取消
     */
    boolean changeStatus(Long id, Integer targetStatus);

    String nextProjectCode();
}
