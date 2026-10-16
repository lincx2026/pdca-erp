package com.pcba.erp.common.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/** Spring 上下文工具 (取任意 Bean 用) */
@Component
public final class SpringUtils implements ApplicationContextAware {
    private static ApplicationContext ctx;

    @Override public void setApplicationContext(ApplicationContext applicationContext) throws BeansException { ctx = applicationContext; }

    public static <T> T getBean(Class<T> t) { return ctx.getBean(t); }
    public static Object getBean(String name) { return ctx.getBean(name); }
}
