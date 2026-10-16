package com.pcba.erp.common.aspectj;

import com.pcba.erp.common.annotation.DataScope;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.common.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 数据权限切面: 进入 @DataScope 方法前, 基于当前用户 dataScope 生成 SQL 片段,
 * 写入 MyBatis-Plus 的请求线程 params(Map)。
 *   - params.dataScope  : AND dept_id IN (...) [AND user_id = X]
 *   - params.deptOnly   : 仅部门子句(不含 AND, 用于多表 join 后自定义拼接)
 *   - params.userOnly   : 仅用户子句
 *
 * dataScope 取值 (sys_role.data_scope):
 *   1 = 全部数据权限 → 不拼接任何条件
 *   2 = 本部门       → dept_id = :myDept
 *   3 = 本部门及子部门 → dept_id IN (SELECT dept_id FROM sys_dept WHERE (CAST(:myDept AS TEXT) = ANY(string_to_array(ancestors, ','))))
 *   4 = 本人         → create_by = :uid (注意 userField 可自定义)
 *   5 = 自定义       → dept_id IN (SELECT dept_id FROM sys_role_dept WHERE role_id in ...) (暂不支持, 等价 2)
 */
@Slf4j
@Aspect
@Component
public class DataScopeAspect {

    /** 1 全部 / 2 本部门 / 3 本部门及以下 / 4 本人 */
    public static final String DATA_SCOPE_ALL         = "1";
    public static final String DATA_SCOPE_DEPT        = "2";
    public static final String DATA_SCOPE_DEPT_AND_SUB= "3";
    public static final String DATA_SCOPE_SELF        = "4";

    @Before("@annotation(dataScope)")
    public void before(JoinPoint jp, DataScope dataScope) throws Throwable {
        LoginUser loginUser = SecurityUtils.getLoginUserSafe();
        if (loginUser == null || loginUser.getDataScope() == null) return;
        // 超级管理员跳过
        if (SecurityUtils.isAdmin()) return;

        StringBuilder sql = new StringBuilder();
        String deptField = dataScope.deptField();
        String userField = dataScope.userField();
        String ds = String.valueOf(loginUser.getDataScope());
        Long deptId = loginUser.getDeptId();
        Long userId = loginUser.getUserId();

        if (DATA_SCOPE_DEPT.equals(ds)) {
            if (deptId == null) { sql.append(" AND 1 = 0"); }
            else sql.append(" AND ").append(deptField).append(" = ").append(deptId);
        } else if (DATA_SCOPE_DEPT_AND_SUB.equals(ds)) {
            if (deptId == null) { sql.append(" AND 1 = 0"); }
            else sql.append(" AND ").append(deptField).append(" IN (")
                    .append("SELECT dept_id FROM sys_dept WHERE \'").append(deptId)
                    .append("\' = ANY(string_to_array(ancestors, \',\')) OR dept_id = ").append(deptId).append(")");
        } else if (DATA_SCOPE_SELF.equals(ds)) {
            // 仅本人
            sql.append(" AND ").append(userField).append(" = \'")
                    .append(loginUser.getUsername().replace("\'", "\'\'")).append("\'");
        } else {
            // ALL 或其它: 无限制
        }
        // 写入 MyBatis params
        Object[] args = jp.getArgs();
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof java.util.Map map) {
                    map.put("dataScope", sql.toString());
                    map.put("deptOnly", sql.toString());
                    map.put("userOnly", " AND " + userField + " = " + (userId == null ? -1 : userId));
                }
            }
        }
        // 兜底: ThreadLocal (给 MyBatis XML 用)
        DataScopeThreadLocal.set(sql.toString());
    }
}
