package com.pcba.erp.system.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pcba.erp.common.annotation.DataScope;
import com.pcba.erp.common.aspectj.DataScopeThreadLocal;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.system.entity.SysUser;
import com.pcba.erp.system.entity.SysUserRole;
import com.pcba.erp.system.mapper.SysDeptMapper;
import com.pcba.erp.system.mapper.SysUserMapper;
import com.pcba.erp.system.mapper.SysUserRoleMapper;
import com.pcba.erp.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysDeptMapper deptMapper;

    @Override
    @DataScope(deptField = "d.dept_id", userField = "u.create_by")
    public IPage<SysUser> selectUserPage(int pageNum, int pageSize, Long deptId, String userName,
                                          Integer status, String phonenumber, String beginTime, String endTime) {
        IPage<SysUser> page = new Page<>(pageNum, pageSize);
        String dataScopeSql = DataScopeThreadLocal.getAndClear();
        return baseMapper.selectUserPage(page, deptId, userName, status, phonenumber, beginTime, endTime, dataScopeSql);
    }

    @Override
    public SysUser selectUserByUserName(String username) {
        return baseMapper.selectUserByUserName(username);
    }

    @Override public boolean checkUserNameUnique(SysUser u) {
        Long id = u.getUserId() == null ? -1L : u.getUserId();
        SysUser ex = lambdaQuery().eq(SysUser::getUserName, u.getUserName()).one();
        return ex == null || ex.getUserId().equals(id);
    }
    @Override public boolean checkPhoneUnique(SysUser u) {
        if (!StringUtils.hasText(u.getPhonenumber())) return true;
        Long id = u.getUserId() == null ? -1L : u.getUserId();
        SysUser ex = lambdaQuery().eq(SysUser::getPhonenumber, u.getPhonenumber()).one();
        return ex == null || ex.getUserId().equals(id);
    }
    @Override public boolean checkEmailUnique(SysUser u) {
        if (!StringUtils.hasText(u.getEmail())) return true;
        Long id = u.getUserId() == null ? -1L : u.getUserId();
        SysUser ex = lambdaQuery().eq(SysUser::getEmail, u.getEmail()).one();
        return ex == null || ex.getUserId().equals(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertUser(SysUser user) {
        // 校验
        if (!checkUserNameUnique(user)) throw new ServiceException("账号已存在: " + user.getUserName());
        if (!checkPhoneUnique(user)) throw new ServiceException("手机号已存在");
        if (!checkEmailUnique(user)) throw new ServiceException("邮箱已存在");
        if (StringUtils.hasText(user.getPassword())) user.setPassword(SecurityUtils.encodePassword(user.getPassword()));
        else user.setPassword(SecurityUtils.encodePassword("123456")); // 默认密码
        if (!StringUtils.hasText(user.getDelFlag())) user.setDelFlag("0");
        if (user.getStatus() == null) user.setStatus(1);
        baseMapper.insert(user);
        // 分配角色
        if (user.getRoleIds() != null && !user.getRoleIds().isEmpty()) {
            userRoleMapper.batchInsert(user.getUserId(), user.getRoleIds());
        }
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateUser(SysUser user) {
        Long uid = user.getUserId();
        if (uid == null) throw new ServiceException("用户ID不能为空");
        if (!checkUserNameUnique(user)) throw new ServiceException("账号已存在");
        if (!checkPhoneUnique(user)) throw new ServiceException("手机号已存在");
        if (!checkEmailUnique(user)) throw new ServiceException("邮箱已存在");
        if (Long.valueOf(1L).equals(uid)) {
            if (Integer.valueOf(0).equals(user.getStatus())) throw new ServiceException("超级管理员不能停用");
        }
        baseMapper.updateById(user);
        // 重新分配角色
        if (user.getRoleIds() != null) {
            userRoleMapper.deleteByUserId(uid);
            if (!user.getRoleIds().isEmpty()) userRoleMapper.batchInsert(uid, user.getRoleIds());
        }
        return 1;
    }

    @Override public int resetUserPwd(Long userId, String newPwd) {
        if (!StringUtils.hasText(newPwd) || newPwd.length() < 5) throw new ServiceException("密码长度不能小于5位");
        SysUser u = new SysUser(); u.setUserId(userId); u.setPassword(SecurityUtils.encodePassword(newPwd));
        return baseMapper.updateById(u);
    }
    @Override public int updateUserStatus(Long userId, Integer status) {
        if (Long.valueOf(1L).equals(userId) && Integer.valueOf(0).equals(status)) throw new ServiceException("超级管理员不能停用");
        SysUser u = new SysUser(); u.setUserId(userId); u.setStatus(status);
        return baseMapper.updateById(u);
    }

    @Override public SysUser selectUserById(Long userId) {
        SysUser u = baseMapper.selectById(userId);
        if (u == null) return null;
        if (u.getDeptId() != null) u.setDept(deptMapper.selectById(u.getDeptId()));
        List<SysUserRole> urs = userRoleMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        u.setRoleIds(urs.stream().map(SysUserRole::getRoleId).collect(Collectors.toList()));
        return u;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteUserByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        if (ids.contains(1L)) throw new ServiceException("不能删除超级管理员");
        for (Long id : ids) {
            SysUser u = getById(id);
            if (u == null) continue;
            if (SecurityUtils.getUserId().equals(id)) throw new ServiceException("不能删除当前登录用户");
        }
        ids.forEach(userRoleMapper::deleteByUserId);
        return baseMapper.deleteBatchIds(ids);
    }

    @Override public void recordLoginInfo(Long userId, String ip) {
        SysUser u = new SysUser();
        u.setUserId(userId);
        u.setLoginIp(ip);
        u.setLoginDate(LocalDateTime.now());
        baseMapper.updateById(u);
    }

    // 修复 "密码默认" 调用点不存在的错误 - 实际上 insertUser 内部已经有默认密码分支，下面一个 placeholder 工具方法：
    public static boolean isDefaultPwdPlaceholder() { return false; }
}
