package com.pcba.erp.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.page.TableDataInfo;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.system.entity.SysRole;
import com.pcba.erp.system.entity.SysUser;
import com.pcba.erp.system.service.ISysRoleService;
import com.pcba.erp.system.service.ISysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final ISysUserService userService;
    private final ISysRoleService roleService;

    @Operation(summary = "分页用户列表")
    @PreAuthorize("@ps.hasPermi('system:user:list')")
    @GetMapping("/list")
    public TableDataInfo<SysUser> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String phonenumber,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime) {
        IPage<SysUser> p = userService.selectUserPage(pageNum, pageSize, deptId, userName, status, phonenumber, beginTime, endTime);
        return TableDataInfo.of(p.getRecords(), p.getTotal());
    }

    @Operation(summary = "用户详情(含已分配角色ID)")
    @PreAuthorize("@ps.hasPermi('system:user:query')")
    @GetMapping("/{id}")
    public R<Map<String, Object>> get(@PathVariable Long id) {
        SysUser u = userService.selectUserById(id);
        List<SysRole> roles = SecurityUtils.isAdmin()
                ? roleService.list()
                : roleService.selectRolesByUserId(SecurityUtils.getUserId());
        return R.ok(Map.of(
                "data", u == null ? new SysUser() : u,
                "roles", roles,
                "roleIds", u == null || u.getRoleIds() == null ? new ArrayList<Long>() : u.getRoleIds()
        ));
    }

    @Operation(summary = "新增用户")
    @PreAuthorize("@ps.hasPermi('system:user:add')")
    @OperLog(title = "用户管理", businessType = 1)
    @PostMapping
    public R<Integer> add(@Validated @RequestBody SysUser user) {
        userService.insertUser(user);
        return R.ok(1);
    }

    @Operation(summary = "修改用户")
    @PreAuthorize("@ps.hasPermi('system:user:edit')")
    @OperLog(title = "用户管理", businessType = 2)
    @PutMapping
    public R<Integer> edit(@Validated @RequestBody SysUser user) {
        if (SecurityUtils.isAdmin() == false
                && user.getUserId() != null
                && !user.getUserId().equals(SecurityUtils.getUserId())) {
            // 非管理员不允许修改别人的关键信息 (通过角色控制, 但这里再挡一层: 不能修改超级管理员资料)
            if (Long.valueOf(1L).equals(user.getUserId())) return R.fail("无权限修改超级管理员");
        }
        userService.updateUser(user);
        return R.ok(1);
    }

    @Operation(summary = "批量删除用户")
    @PreAuthorize("@ps.hasPermi('system:user:remove')")
    @OperLog(title = "用户管理", businessType = 3)
    @DeleteMapping("/{ids}")
    public R<Integer> remove(@PathVariable List<Long> ids) {
        return R.ok(userService.deleteUserByIds(ids));
    }

    @Operation(summary = "重置密码")
    @PreAuthorize("@ps.hasPermi('system:user:resetPwd')")
    @OperLog(title = "用户管理", businessType = 2)
    @PutMapping("/reset-pwd")
    public R<Integer> resetPwd(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        String password = String.valueOf(body.get("password"));
        if (!SecurityUtils.isAdmin() && !userId.equals(SecurityUtils.getUserId())) {
            return R.fail("无权限重置他人密码");
        }
        return R.ok(userService.resetUserPwd(userId, password));
    }

    @Operation(summary = "修改用户状态(启用/停用)")
    @PreAuthorize("@ps.hasPermi('system:user:edit')")
    @OperLog(title = "用户管理", businessType = 2)
    @PutMapping("/change-status")
    public R<Integer> changeStatus(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        Integer status = Integer.valueOf(String.valueOf(body.get("status")));
        return R.ok(userService.updateUserStatus(userId, status));
    }

    @Operation(summary = "获取用户可选角色(表单下拉)")
    @PreAuthorize("@ps.hasPermi('system:user:list')")
    @GetMapping("/optionselect")
    public R<List<Map<String, Object>>> optionSelect() {
        List<SysRole> roles = SecurityUtils.isAdmin()
                ? roleService.lambdaQuery().eq(SysRole::getStatus, 0).orderByAsc(SysRole::getRoleSort).list()
                : roleService.selectRolesByUserId(SecurityUtils.getUserId());
        List<Map<String, Object>> list = roles.stream()
                .map(r -> Map.<String, Object>of(
                        "roleId", r.getRoleId(),
                        "roleName", r.getRoleName(),
                        "roleKey", r.getRoleKey()
                )).collect(Collectors.toList());
        return R.ok(list);
    }
}
