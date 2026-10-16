package com.pcba.erp.pdm.service;

import com.pcba.erp.pdm.entity.PdmMaterialSupplier;

import java.util.List;

public interface IPdmMaterialSupplierService {

    /** 按物料查询供应商报价列表（含 dataScope）*/
    List<PdmMaterialSupplier> listByMaterial(Long materialId, Long supplierId, Integer status);

    PdmMaterialSupplier getById(Long id);

    boolean save(PdmMaterialSupplier s);

    boolean updateById(PdmMaterialSupplier s);

    /**
     * 批量保存(物料页 Tab4 的提交): 按 materialId 全量覆盖
     * 先 material_id 全部删除再重新插入列表，事务包裹
     */
    boolean replaceAll(Long materialId, List<PdmMaterialSupplier> list);

    boolean removeById(Long id);
}
