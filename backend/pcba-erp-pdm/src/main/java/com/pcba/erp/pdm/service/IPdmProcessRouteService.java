package com.pcba.erp.pdm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pdm.entity.PdmProcessRoute;

public interface IPdmProcessRouteService {

    /** 分页查询 */
    IPage<PdmProcessRoute> selectRoutePage(int pageNum, int pageSize,
                                           String routeCode, String routeName,
                                           Long productId, String productCode, Integer status);

    /** 详情（含步骤列表 + 冗余统计字段） */
    PdmProcessRoute getDetail(Long routeId);

    /** 生成工艺路线编码 */
    String nextRouteCode();

    /** 新增（主档+步骤一次性） */
    Long saveWithSteps(PdmProcessRoute route);

    /** 修改（主档） */
    boolean update(PdmProcessRoute route);

    /** 删除（仅停用可删, 级联步骤） */
    boolean remove(Long routeId);

    /** 全量覆盖步骤（replaceAll） */
    boolean replaceSteps(Long routeId, java.util.List<com.pcba.erp.pdm.entity.PdmProcessStep> steps);

    /** 设为默认工艺路线（同产品唯一） */
    boolean setDefault(Long routeId);

    /** 停用 */
    boolean disable(Long routeId);

    /** 启用 */
    boolean enable(Long routeId);

    /** 复制路线（复制主档+步骤, 返回新路线ID） */
    Long copy(Long sourceRouteId, String newRouteName);

    /** 根据产品ID查默认启用的路线 */
    PdmProcessRoute getDefaultRoute(Long productId);
}
