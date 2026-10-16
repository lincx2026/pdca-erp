package com.pcba.erp.system.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pcba.erp.common.event.OperLogEvent;
import com.pcba.erp.system.entity.SysOperLog;
import com.pcba.erp.system.mapper.SysOperLogMapper;
import com.pcba.erp.system.service.ISysOperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service("sysOperLogService")
@RequiredArgsConstructor
public class SysOperLogServiceImpl extends ServiceImpl<SysOperLogMapper, SysOperLog> implements ISysOperLogService {

    @Override
    public IPage<SysOperLog> selectPage(int pageNum, int pageSize, String title, Integer businessType,
                                        Integer status, String operName, String beginTime, String endTime) {
        return baseMapper.selectPage(new Page<>(pageNum, pageSize), title, businessType, status, operName, beginTime, endTime);
    }

    @Override public void clean() {
        baseMapper.delete(null);
    }

    /**
     * 接收事件入库 (注意: 不要改方法名和参数类型, common 层事件监听器用反射调用这里)
     */
    @Override @Async
    public void saveEvent(OperLogEvent event) {
        try {
            baseMapper.insertByEvent(event);
        } catch (Exception ignore) { /* 日志写失败不能影响主流程 */ }
    }
}
