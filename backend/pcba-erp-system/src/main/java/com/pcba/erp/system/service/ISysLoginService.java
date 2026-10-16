package com.pcba.erp.system.service;

import com.pcba.erp.common.security.LoginUser;

/**
 * 登录服务 (认证 + 写入 Redis)
 */
public interface ISysLoginService {
    /** 登录, 返回 JWT token */
    String login(String username, String password);

    /** 构建 LoginUser (对外暴露, 可扩展) */
    LoginUser buildLoginUser(String username);
}
