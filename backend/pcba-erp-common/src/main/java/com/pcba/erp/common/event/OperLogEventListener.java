package com.pcba.erp.common.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 监听 OperLogEvent, 异步写入 sys_oper_log。
 * 注意：使用反射调用 System 模块的 Service 避免 common 循环依赖 system。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperLogEventListener {

    private final org.springframework.context.ApplicationContext ctx;

    @Async
    @EventListener
    public void onEvent(OperLogEvent e) {
        try {
            Object bean = ctx.getBean("sysOperLogService");
            Method m = bean.getClass().getMethod("saveEvent", OperLogEvent.class);
            m.invoke(bean, e);
        } catch (Exception ex) {
            log.warn("操作日志保存失败 (sysOperLogService 未就绪或无 saveEvent 方法): {}", ex.getMessage());
        }
    }
}
