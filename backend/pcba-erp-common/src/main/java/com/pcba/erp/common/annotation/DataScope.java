package com.pcba.erp.common.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解, 加在 Service 层分页/列表查询方法上。
 * 根据当前登录用户的 dataScope 自动拼接 AND dept_id IN (...) 或 AND user_id = :uid SQL 片段。
 * 该注解配合 DataScopeAspect 注入 params, MyBatis 的 XML / Wrapper 引用 ${params.dataScope} 即可。
 *
 * deptField = "d.dept_id" / userField = "u.user_id" 自定义 SQL 中的别名前缀。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {
    /** 部门表别名/字段, 默认 dept_id, 例: 给 d.dept_id 就填 "d.dept_id" */
    String deptField() default "dept_id";
    /** 用户表别名/字段 */
    String userField() default "create_by";
}
