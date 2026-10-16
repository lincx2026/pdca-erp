package com.pcba.erp.system.service.impl;

import com.pcba.erp.common.annotation.DataScope;
import com.pcba.erp.common.aspectj.DataScopeThreadLocal;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.system.entity.SysDept;
import com.pcba.erp.system.entity.SysUser;
import com.pcba.erp.system.mapper.SysDeptMapper;
import com.pcba.erp.system.mapper.SysUserMapper;
import com.pcba.erp.system.service.ISysDeptService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements ISysDeptService {

    private final SysUserMapper userMapper;

    @Override
    @DataScope(deptField = "d.dept_id")
    public List<SysDept> selectDeptList(String deptName, Integer status) {
        String dataScopeSql = DataScopeThreadLocal.getAndClear();
        List<SysDept> flat = baseMapper.selectDeptList(deptName, status, dataScopeSql);
        return buildTree(flat);
    }

    @Override
    public boolean checkDeptNameUnique(SysDept dept) {
        Long pid = dept.getParentId() == null ? 0L : dept.getParentId();
        Long id = dept.getDeptId() == null ? -1L : dept.getDeptId();
        SysDept exist = lambdaQuery().eq(SysDept::getParentId, pid).eq(SysDept::getDeptName, dept.getDeptName()).one();
        return exist == null || exist.getDeptId().equals(id);
    }

    @Override
    public boolean hasChildByDeptId(Long deptId) {
        return lambdaQuery().eq(SysDept::getParentId, deptId).count() > 0;
    }

    @Override
    public boolean checkDeptExistUser(Long deptId) {
        Long count = userMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, deptId));
        return count != null && count > 0;
    }

    @Override
    public boolean save(SysDept dept) {
        // 1. 同级唯一
        if (!checkDeptNameUnique(dept)) throw new ServiceException("同层级已存在部门名:" + dept.getDeptName());
        // 2. 父级信息: ancestors 字段
        Long pid = dept.getParentId() == null ? 0L : dept.getParentId();
        if (pid == 0L) dept.setAncestors("0");
        else {
            SysDept parent = getById(pid);
            if (parent == null) throw new ServiceException("父部门不存在");
            if (parent.getStatus() != null && parent.getStatus() == 1) throw new ServiceException("父部门停用, 不能新增子部门");
            dept.setAncestors(parent.getAncestors() + "," + pid);
        }
        if (!StringUtils.hasText(dept.getDelFlag())) dept.setDelFlag("0");
        return super.save(dept);
    }

    @Override
    public boolean updateById(SysDept dept) {
        Long pid = dept.getParentId() == null ? 0L : dept.getParentId();
        Long oldParentId = getById(dept.getDeptId()) != null ? getById(dept.getDeptId()).getParentId() : 0L;
        if (!checkDeptNameUnique(dept)) throw new ServiceException("同层级已存在部门名:" + dept.getDeptName());
        // 若 parentId 变更 → 重算 ancestors + 递归子部门
        if (!pid.equals(oldParentId)) {
            SysDept parent = pid == 0L ? null : getById(pid);
            String newAnc = (pid == 0L) ? "0" : parent.getAncestors() + "," + pid;
            String oldAnc = dept.getAncestors();
            dept.setAncestors(newAnc);
            // 批量更新子部门: ancestors 前缀替换
            if (oldAnc != null) {
                List<SysDept> children = lambdaQuery().likeRight(SysDept::getAncestors, oldAnc + "," + dept.getDeptId()).list();
                for (SysDept c : children) {
                    c.setAncestors(c.getAncestors().replaceFirst("^" + oldAnc + "," + dept.getDeptId(), newAnc + "," + dept.getDeptId()));
                    updateById(c);
                }
            }
        }
        return super.updateById(dept);
    }

    private List<SysDept> buildTree(List<SysDept> list) {
        Map<Long, List<SysDept>> group = list.stream().collect(Collectors.groupingBy(d -> d.getParentId() == null ? 0L : d.getParentId()));
        List<SysDept> roots = group.getOrDefault(0L, new ArrayList<>());
        for (SysDept r : list) r.setChildren(group.getOrDefault(r.getDeptId(), null));
        return roots;
    }
}
