package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.PmmJig;

import java.util.List;

/**
 * 工装治具档案 Service
 */
public interface IPmmJigService {

    IPage<PmmJig> selectPage(int pageNum, int pageSize,
                             String jigNo, String jigName, String jigType,
                             String productCode, String productName, Integer status,
                             String keeper, String warehouse, String location, String borrowerName);

    PmmJig getDetail(Long jigId);

    /** 新增工装: 编码查重 + 默认值初始化 + 下次校准 */
    Long save(PmmJig entity);

    boolean update(PmmJig entity);

    /** 删除: 只有 status=报废 允许直接物理删除(归档删除); 其他状态禁止删除应走报废流程 */
    boolean remove(Long jigId);

    /** 下拉字典(含机种过滤) */
    List<PmmJig> options(String productCode);

    // ----------- 6 大业务动作(同时写日志 + 同步主表) -----------
    /** 领用 */
    Long borrow(Long jigId, com.pcba.erp.pmm.entity.PmmJigLog log);

    /** 归还(归还日期 + 本次使用次数usedCountAdd) */
    Long returnBack(Long jigId, com.pcba.erp.pmm.entity.PmmJigLog log);

    /** 校准(钢网张力/针床探针/扭力扳手扭矩) */
    Long calibrate(Long jigId, com.pcba.erp.pmm.entity.PmmJigLog log);

    /** 修磨(钢网重张/更换钢片/分板治具PIN/海绵) → used_count 重置 */
    Long resharpen(Long jigId, com.pcba.erp.pmm.entity.PmmJigLog log);

    /** 维修 */
    Long repair(Long jigId, com.pcba.erp.pmm.entity.PmmJigLog log);

    /** 报废(扣总数 qty_current/qty_available) */
    Long scrap(Long jigId, com.pcba.erp.pmm.entity.PmmJigLog log);
}
