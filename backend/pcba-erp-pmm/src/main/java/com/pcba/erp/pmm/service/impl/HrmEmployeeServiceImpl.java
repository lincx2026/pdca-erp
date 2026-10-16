package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.HrmEmployee;
import com.pcba.erp.pmm.mapper.HrmEmployeeMapper;
import com.pcba.erp.pmm.service.IHrmEmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HrmEmployeeServiceImpl implements IHrmEmployeeService {

    private final HrmEmployeeMapper mapper;

    @Override
    public IPage<HrmEmployee> selectPage(int pageNum, int pageSize,
                                         String employeeNo, String name, String phone,
                                         Long deptId, Integer status) {
        return mapper.selectPage(new Page<>(pageNum, pageSize),
                employeeNo, name, phone, deptId, status);
    }

    @Override
    public HrmEmployee getDetail(Long id) {
        if (id == null) throw new ServiceException("员工ID不能为空");
        HrmEmployee e = mapper.selectById(id);
        if (e == null) throw new ServiceException("员工不存在");
        // 填充部门名
        if (e.getDeptId() != null) {
            try {
                List<Map<String, Object>> depts = mapper.selectDeptList();
                depts.stream()
                        .filter(d -> ((Number) d.get("deptId")).longValue() == e.getDeptId())
                        .findFirst()
                        .ifPresent(d -> e.setDeptName((String) d.get("deptName")));
            } catch (Exception ignored) {}
        }
        return e;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(HrmEmployee entity) {
        if (entity.getName() == null || entity.getName().trim().isEmpty())
            throw new ServiceException("员工姓名不能为空");
        if (entity.getEmployeeNo() == null || entity.getEmployeeNo().trim().isEmpty())
            throw new ServiceException("工号不能为空");
        if (entity.getStatus() == null) entity.setStatus(1);
        mapper.insert(entity);
        return entity.getEmployeeId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(HrmEmployee entity) {
        if (entity.getEmployeeId() == null) throw new ServiceException("员工ID不能为空");
        if (mapper.selectById(entity.getEmployeeId()) == null) throw new ServiceException("员工不存在");
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        if (id == null) throw new ServiceException("员工ID不能为空");
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<Map<String, Object>> deptTree() {
        List<Map<String, Object>> all = mapper.selectDeptList();
        // 转换类型
        List<Map<String, Object>> nodes = all.stream().map(m -> {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("deptId", ((Number) m.get("deptId")).longValue());
            node.put("parentId", m.get("parentId") == null ? 0L : ((Number) m.get("parentId")).longValue());
            node.put("deptName", m.get("deptName"));
            node.put("orderNum", m.get("orderNum"));
            node.put("children", new ArrayList<Map<String, Object>>());
            return node;
        }).collect(Collectors.toList());

        Map<Long, List<Map<String, Object>>> childrenMap = nodes.stream()
                .filter(n -> (Long) n.get("parentId") != 0L)
                .collect(Collectors.groupingBy(n -> (Long) n.get("parentId")));

        List<Map<String, Object>> roots = nodes.stream()
                .filter(n -> (Long) n.get("parentId") == 0L)
                .collect(Collectors.toList());

        fillChildren(roots, childrenMap);
        return roots;
    }

    @SuppressWarnings("unchecked")
    private void fillChildren(List<Map<String, Object>> nodes, Map<Long, List<Map<String, Object>>> childrenMap) {
        for (Map<String, Object> node : nodes) {
            Long id = (Long) node.get("deptId");
            List<Map<String, Object>> children = childrenMap.get(id);
            if (children != null && !children.isEmpty()) {
                node.put("children", children);
                fillChildren(children, childrenMap);
            }
        }
    }
}
