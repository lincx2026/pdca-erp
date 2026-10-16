package com.pcba.erp.pdm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.entity.PdmCustomerMaterialMap;
import com.pcba.erp.pdm.entity.PdmMaterial;
import com.pcba.erp.pdm.mapper.PdmCustomerMaterialMapMapper;
import com.pcba.erp.pdm.mapper.PdmMaterialMapper;
import com.pcba.erp.pdm.service.IPdmCustomerMaterialMapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdmCustomerMaterialMapServiceImpl
        extends ServiceImpl<PdmCustomerMaterialMapMapper, PdmCustomerMaterialMap>
        implements IPdmCustomerMaterialMapService {

    private final PdmCustomerMaterialMapMapper mapMapper;
    private final PdmMaterialMapper materialMapper;
    private final JdbcTemplate jdbcTemplate;

    /* ========== 分页查询 ========== */
    @Override
    public IPage<PdmCustomerMaterialMap> selectMapPage(int pageNum, int pageSize,
                                                        Long customerId, String customerName,
                                                        Long materialId, String customerMaterialNo,
                                                        String materialCode, Integer status) {
        Page<PdmCustomerMaterialMap> page = new Page<>(pageNum, pageSize);
        return mapMapper.selectMapPage(page, customerId, customerName,
                materialId, customerMaterialNo, materialCode, status);
    }

    /* ========== 详情 ========== */
    @Override
    public PdmCustomerMaterialMap getDetail(Long mapId) {
        PdmCustomerMaterialMap map = mapMapper.selectById(mapId);
        if (map == null) throw new ServiceException("记录不存在");
        // 联表回填
        fillJoinFields(map);
        return map;
    }

    /* ========== 新增 ========== */
    @Override
    @Transactional
    public Long saveMap(PdmCustomerMaterialMap map) {
        // 唯一性校验
        if (mapMapper.checkUnique(map.getCustomerId(), map.getCustomerMaterialNo(), null) > 0) {
            throw new ServiceException("该客户料号已存在映射");
        }
        // 校验物料存在
        PdmMaterial mat = materialMapper.selectById(map.getMaterialId());
        if (mat == null) throw new ServiceException("工厂物料不存在");
        // 回填客户名称
        map.setCustomerName(fetchCustomerName(map.getCustomerId()));
        if (map.getStatus() == null) map.setStatus(1);
        mapMapper.insert(map);
        return map.getMapId();
    }

    /* ========== 修改 ========== */
    @Override
    @Transactional
    public boolean updateMap(PdmCustomerMaterialMap map) {
        if (map.getMapId() == null) throw new ServiceException("ID不能为空");
        // 唯一性校验
        if (mapMapper.checkUnique(map.getCustomerId(), map.getCustomerMaterialNo(), map.getMapId()) > 0) {
            throw new ServiceException("该客户料号已存在映射");
        }
        // 校验物料存在
        if (map.getMaterialId() != null) {
            PdmMaterial mat = materialMapper.selectById(map.getMaterialId());
            if (mat == null) throw new ServiceException("工厂物料不存在");
        }
        // 回填客户名称
        if (map.getCustomerId() != null) {
            map.setCustomerName(fetchCustomerName(map.getCustomerId()));
        }
        return mapMapper.updateById(map) > 0;
    }

    /* ========== 删除 ========== */
    @Override
    @Transactional
    public boolean removeMap(Long mapId) {
        return mapMapper.deleteById(mapId) > 0;
    }

    /* ========== 启用/停用 ========== */
    @Override
    public boolean toggleStatus(Long mapId, Integer status) {
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PdmCustomerMaterialMap> w =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        w.eq(PdmCustomerMaterialMap::getMapId, mapId).set(PdmCustomerMaterialMap::getStatus, status);
        return mapMapper.update(null, w) > 0;
    }

    /* ========== 反查 ========== */
    @Override
    public PdmCustomerMaterialMap reverseLookup(String customerMaterialNo) {
        return mapMapper.selectByCustomerMaterialNo(customerMaterialNo);
    }

    /* ========== 批量导入 ========== */
    @Override
    @Transactional
    public String batchImport(List<PdmCustomerMaterialMap> list, boolean updateSupport) {
        int insertCount = 0, updateCount = 0, skipCount = 0;
        for (PdmCustomerMaterialMap map : list) {
            int exists = mapMapper.checkUnique(map.getCustomerId(), map.getCustomerMaterialNo(), null);
            if (exists > 0) {
                if (updateSupport) {
                    // 按客户+料号找到已有记录，更新
                    PdmCustomerMaterialMap existing = mapMapper.selectByCustomerMaterialNo(map.getCustomerMaterialNo());
                    if (existing != null) {
                        map.setMapId(existing.getMapId());
                        map.setCustomerName(fetchCustomerName(map.getCustomerId()));
                        mapMapper.updateById(map);
                        updateCount++;
                    } else {
                        skipCount++;
                    }
                } else {
                    skipCount++;
                }
            } else {
                map.setCustomerName(fetchCustomerName(map.getCustomerId()));
                if (map.getStatus() == null) map.setStatus(1);
                mapMapper.insert(map);
                insertCount++;
            }
        }
        return String.format("导入完成：新增 %d 条，更新 %d 条，跳过 %d 条", insertCount, updateCount, skipCount);
    }

    /* ========== 私有方法 ========== */
    private void fillJoinFields(PdmCustomerMaterialMap map) {
        if (map.getMaterialId() != null) {
            PdmMaterial mat = materialMapper.selectById(map.getMaterialId());
            if (mat != null) {
                map.setMaterialCode(mat.getMaterialCode());
                map.setMaterialName(mat.getMaterialName());
                map.setSpecification(mat.getSpecification());
            }
        }
        if (map.getCustomerId() != null) {
            map.setCustomerCode(fetchCustomerCode(map.getCustomerId()));
        }
    }

    private String fetchCustomerName(Long customerId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT customer_name FROM scm_customer WHERE customer_id = ?",
                    String.class, customerId);
        } catch (Exception e) {
            return "";
        }
    }

    private String fetchCustomerCode(Long customerId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT customer_code FROM scm_customer WHERE customer_id = ?",
                    String.class, customerId);
        } catch (Exception e) {
            return "";
        }
    }
}
