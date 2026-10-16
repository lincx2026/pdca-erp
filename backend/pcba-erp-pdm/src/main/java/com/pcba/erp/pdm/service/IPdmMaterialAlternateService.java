package com.pcba.erp.pdm.service;

import com.pcba.erp.pdm.entity.PdmMaterialAlternate;

import java.util.List;

public interface IPdmMaterialAlternateService {

    /** 查询 */
    List<PdmMaterialAlternate> listAlternates(Long mainMaterialId, Long altMaterialId,
                                              String groupId, Integer status);

    PdmMaterialAlternate getById(Long id);

    /** 生成组号 ALT-yyMMddHHmmss + 2位随机 */
    String nextGroupId();

    boolean save(PdmMaterialAlternate a);

    boolean updateById(PdmMaterialAlternate a);

    boolean removeById(Long id);

    /**
     * 批量保存(物料页 Tab5 全量覆盖)
     */
    boolean replaceAll(Long mainMaterialId, List<PdmMaterialAlternate> list);

    // ============= 状态机 =============
    boolean submitAudit(Long id);
    boolean auditPass(Long id, String auditBy);
    boolean auditReject(Long id, String auditBy, String rejectReason);
    boolean disable(Long id);
}
