package com.pcba.erp.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pcba.erp.system.entity.SysUser;

import java.util.List;

public interface ISysUserService extends IService<SysUser> {
    /** 分页查询用户 (带数据范围, @DataScope 拦截) */
    IPage<SysUser> selectUserPage(int pageNum, int pageSize, Long deptId, String userName,
                                  Integer status, String phonenumber, String beginTime, String endTime);

    /** 按用户名查 (登录) */
    SysUser selectUserByUserName(String userName);

    /** 校验账号唯一 */
    boolean checkUserNameUnique(SysUser user);
    boolean checkPhoneUnique(SysUser user);
    boolean checkEmailUnique(SysUser user);

    /** 新增用户 (含角色分配) */
    int insertUser(SysUser user);

    /** 修改用户 (含角色分配) */
    int updateUser(SysUser user);

    /** 重置密码 */
    int resetUserPwd(Long userId, String newPwd);

    /** 更新用户状态 */
    int updateUserStatus(Long userId, Integer status);

    /** 根据 ID 查 (带 dept + roleIds) */
    SysUser selectUserById(Long userId);

    /** 批量删除 */
    int deleteUserByIds(List<Long> ids);

    /** 更新登录信息 (登录成功后调用) */
    void recordLoginInfo(Long userId, String ip);
}
