package com.pcba.erp.pdm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pdm.entity.PdmMaterial;
import com.pcba.erp.pdm.excel.MaterialImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * 物料主档 Service
 */
public interface IPdmMaterialService {

    /** 分页查询 */
    IPage<PdmMaterial> selectMaterialPage(int pageNum, int pageSize,
                                           Long categoryId, String materialCode,
                                           String materialName, String materialType,
                                           String mpn, Integer status);

    PdmMaterial getById(Long materialId);

    /** 保存物料 (默认状态 0草稿) */
    boolean save(PdmMaterial material);

    boolean updateById(PdmMaterial material);

    boolean removeById(Long materialId);

    /**
     * 提交审核: 状态 0→1
     */
    boolean submitAudit(Long materialId);

    /**
     * 审核通过: 1→2 已启用
     */
    boolean auditPass(Long materialId, String auditBy);

    /**
     * 审核驳回: 1→4 已驳回, 填 rejectReason
     */
    boolean auditReject(Long materialId, String auditBy, String rejectReason);

    /**
     * 停用/失效: 2→3
     */
    boolean disable(Long materialId);

    /* ================ 导入导出(W3.2) ================ */

    /**
     * 按查询条件取最多 10000 条用于导出(不分页，拉平 list)
     */
    List<PdmMaterial> listForExport(Long categoryId, String materialCode,
                                    String materialName, String materialType,
                                    String mpn, Integer status);

    /**
     * 写 Excel 模板（带下拉 + 30列表头）到 out
     */
    void exportTemplate(OutputStream out) throws IOException;

    /**
     * 写 Excel(按查询条件) 到 out
     */
    void exportToStream(OutputStream out, Long categoryId, String materialCode,
                        String materialName, String materialType,
                        String mpn, Integer status) throws IOException;

    /**
     * 导入物料
     * @param file   上传的 Excel 文件
     * @param updateSupport 若编码已存在是否更新(默认 false=仅新增)
     * @return 导入结果(成功N/失败M/失败明细)
     */
    MaterialImportResult importMaterials(MultipartFile file, boolean updateSupport) throws IOException;
}
