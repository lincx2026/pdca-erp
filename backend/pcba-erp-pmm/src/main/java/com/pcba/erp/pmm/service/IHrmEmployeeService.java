package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.HrmEmployee;

import java.util.List;
import java.util.Map;

public interface IHrmEmployeeService {

    IPage<HrmEmployee> selectPage(int pageNum, int pageSize,
                                  String employeeNo, String name, String phone,
                                  Long deptId, Integer status);

    HrmEmployee getDetail(Long id);

    Long save(HrmEmployee entity);

    boolean update(HrmEmployee entity);

    boolean remove(Long id);

    List<Map<String, Object>> deptTree();
}
