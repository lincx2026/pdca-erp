package com.pcba.erp.pdm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pdm.entity.PdmBom;
import com.pcba.erp.pdm.entity.PdmBomLine;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * BOM 管理 Service
 * - 主档 CRUD + 状态机
 * - 行项 replaceAll(先删后插) + 多层BOM递归 + Where-Used反查 + 批量替换子件
 */
public interface IPdmBomService {

    /** 分页查询（含数据权限） */
    IPage<PdmBom> selectBomPage(int pageNum, int pageSize,
                                String bomCode, String bomName, String productMaterialCode,
                                Long productMaterialId, Long categoryId,
                                Integer bomType, Integer status, String version);

    PdmBom getById(Long bomId);

    /** 带行项详情 */
    PdmBom getDetail(Long bomId);

    /** BOM编码自动生成: BOM-yyyyMMdd-HHmmss */
    String nextBomCode();

    /** 保存主档 + 行项(若有 lines), 返回新bomId */
    Long saveWithLines(PdmBom bom);

    /** 更新主档(状态校验: 启用不可修改,除非停用/归档) */
    boolean update(PdmBom bom);

    /** 全量覆盖 BOM 行项（草稿BOM才能用） */
    boolean replaceLines(Long bomId, List<PdmBomLine> lines);

    /** 删除(仅草稿/驳回可删) */
    boolean remove(Long bomId);

    /* ============== 状态机 ============== */
    boolean submitAudit(Long bomId);
    boolean auditPass(Long bomId, String auditBy);
    boolean auditReject(Long bomId, String auditBy, String rejectReason);
    boolean disable(Long bomId);      // 启用 → 停用
    boolean archive(Long bomId);      // 停用 → 归档(不可再编辑)

    /* ============== BOM 展开 ============== */
    /** 某BOM的单层子件(父=成品) */
    List<PdmBomLine> listSingleLevel(Long bomId);

    /** 多层递归展开(最多 levelMax 层, 默认10)；phantomExpand=true 时虚拟件自动下沉展开 */
    List<PdmBomLine> explodeTree(Long bomId, int levelMax, boolean phantomExpand);

    /** BOM 扁平化(叶子物料累计用量汇总) — 用于采购/MRP */
    Map<Long, PdmBomLine> explodeFlatSum(Long bomId, int levelMax, boolean phantomExpand);

    /* ============== Where-Used ============== */
    /** 单层反查: 物料X出现在哪些BOM行 */
    List<PdmBomLine> whereUsedSingle(Long componentId, Integer lineStatus, Integer bomStatus);

    /** 递归反查: 一直查到顶层成品，返回扁平化"受影响BOM ID + 路径" */
    List<Map<String, Object>> whereUsedRecursive(Long componentId, Integer lineStatus, Integer bomStatus, int levelMax);

    /* ============== 批量操作 ============== */
    /** 同BOM内批量替换子件: oldComponentId → newComponentId (可保留或清空替代组) */
    int replaceComponent(Long bomId, Long oldComponentId, Long newComponentId, boolean keepAltGroup, String remark);

    /* ============== Excel 导入 ============== */
    /** 下载 BOM 导入模板（含下拉、示例行） */
    void exportBomTemplate(OutputStream out) throws java.io.IOException;

    /**
     * 预览导入：解析 xlsx → 校验 → 返回预览 JSON（不入库）
     * 返回 Map 含：bomHeader(基本参数), items(行明细), errors(错误行), warnings(警告),
     *              precreateMaterials(待预创建草稿物料列表), totalLines, errorCount, warningCount
     */
    Map<String, Object> previewBomImport(MultipartFile file,
                                          Long productMaterialId,
                                          String bomName,
                                          String version) throws java.io.IOException;

    /**
     * 确认导入：根据预览数据创建草稿物料 + BOM(头+行)，返回 bomId
     * previewData = 前端回传的预览 JSON（含 items + precreateMaterials）
     */
    Long confirmBomImport(Map<String, Object> previewData);
}
