package com.pcba.erp.pdm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pcba.erp.pdm.entity.PdmCustomerMaterialMap;

import java.util.List;

/**
 * 客户料号映射 Service
 */
public interface IPdmCustomerMaterialMapService extends IService<PdmCustomerMaterialMap> {

    /** 分页查询（关联物料 + 客户） */
    IPage<PdmCustomerMaterialMap> selectMapPage(int pageNum, int pageSize,
                                                Long customerId, String customerName,
                                                Long materialId, String customerMaterialNo,
                                                String materialCode, Integer status);

    /** 详情（含联表信息） */
    PdmCustomerMaterialMap getDetail(Long mapId);

    /** 新增（含唯一性校验 + 回填客户名） */
    Long saveMap(PdmCustomerMaterialMap map);

    /** 修改 */
    boolean updateMap(PdmCustomerMaterialMap map);

    /** 删除 */
    boolean removeMap(Long mapId);

    /** 启用/停用 */
    boolean toggleStatus(Long mapId, Integer status);

    /** 按客户料号反查工厂物料 */
    PdmCustomerMaterialMap reverseLookup(String customerMaterialNo);

    /** 批量导入（新增/更新） */
    String batchImport(List<PdmCustomerMaterialMap> list, boolean updateSupport);
}
