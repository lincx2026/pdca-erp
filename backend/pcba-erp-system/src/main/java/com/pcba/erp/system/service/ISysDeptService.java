package com.pcba.erp.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pcba.erp.system.entity.SysDept;

import java.util.List;

public interface ISysDeptService extends IService<SysDept> {
    /** 查询部门列表(树形, 带数据范围) */
    List<SysDept> selectDeptList(String deptName, Integer status);

    /** 检查部门名是否同层级唯一 */
    boolean checkDeptNameUnique(SysDept dept);

    /** 是否有下级部门 */
    boolean hasChildByDeptId(Long deptId);

    /** 部门下是否存在用户 */
    boolean checkDeptExistUser(Long deptId);
}
