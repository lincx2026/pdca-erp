package com.pcba.erp.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pcba.erp.common.event.OperLogEvent;
import com.pcba.erp.system.entity.SysOperLog;

public interface ISysOperLogService extends IService<SysOperLog> {
    /** 分页查询 */
    IPage<SysOperLog> selectPage(int pageNum, int pageSize, String title, Integer businessType,
                                 Integer status, String operName, String beginTime, String endTime);
    /** 清空日志 */
    void clean();
    /** 通过事件入库 (被 common 层 OperLogEventListener 反射调用, 方法名/签名不要改!) */
    void saveEvent(OperLogEvent event);
}
