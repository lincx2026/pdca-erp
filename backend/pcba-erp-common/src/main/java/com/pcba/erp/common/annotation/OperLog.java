package com.pcba.erp.common.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解, 加在 Controller 方法上自动记录操作日志(sys_oper_log)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /** 模块标题, 比如 "用户管理" */
    String title() default "";

    /** 业务类型: 0=其它 1=新增 2=修改 3=删除 4=授权 5=导出 6=导入 7=强退 8=生成代码 9=清空数据 */
    int businessType() default 0;

    /** 操作类型: 0=其它 1=后台用户 2=手机端用户 */
    int operatorType() default 1;

    /** 是否保存请求参数 */
    boolean saveRequestData() default true;

    /** 是否保存响应数据 */
    boolean saveResponseData() default false;

    /** 排除的请求参数名(比如 password) */
    String[] excludeParamNames() default {"password", "oldPassword", "newPassword"};
}
