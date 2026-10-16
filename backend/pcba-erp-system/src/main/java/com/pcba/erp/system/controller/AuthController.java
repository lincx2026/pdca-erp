package com.pcba.erp.system.controller;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.common.security.LoginUser;
import com.pcba.erp.system.entity.SysMenu;
import com.pcba.erp.system.entity.SysUser;
import com.pcba.erp.system.service.ISysLoginService;
import com.pcba.erp.system.service.ISysMenuService;
import com.pcba.erp.system.service.ISysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "认证中心", description = "登录/登出/当前用户信息/验证码")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final ISysLoginService loginService;
    private final ISysMenuService menuService;
    private final ISysUserService userService;

    @Operation(summary = "账号密码登录,返回 JWT token")
    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        @NotBlank String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank()) return R.fail("用户名不能为空");
        if (password == null || password.isBlank()) return R.fail("密码不能为空");
        String token = loginService.login(username.trim(), password);
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("token", token);
        return R.ok(data);
    }

    @Operation(summary = "获取当前登录用户信息(基本信息/角色key列表/权限集/菜单树)", hidden = true)
    @GetMapping("/get-info")
    public R<Map<String, Object>> getInfo() {
        LoginUser u = SecurityUtils.getLoginUser();
        SysUser user = userService.getById(u.getUserId());
        Map<String, Object> map = new LinkedHashMap<>(16);
        map.put("user", user == null ? buildSimpleUser(u) : user);
        // user.avatar 等扩展: dept
        if (user != null && user.getDeptId() != null) {
            try {
                Object dept = com.pcba.erp.common.utils.SpringUtils.getBean("sysDeptServiceImpl").getClass()
                        .getMethod("getById", Object.class).invoke(
                        com.pcba.erp.common.utils.SpringUtils.getBean("sysDeptServiceImpl"), user.getDeptId());
                if (dept != null) user.setDept((com.pcba.erp.system.entity.SysDept) dept);
            } catch (Exception ignore) { /* ignore */ }
        }
        map.put("roles", new ArrayList<>(u.getRoles() == null ? Collections.emptyList() : u.getRoles()));
        map.put("permissions", new ArrayList<>(u.getPermissions() == null ? Collections.emptyList() : u.getPermissions()));
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(u.getUserId());
        map.put("menus", menus);
        map.put("dataScope", u.getDataScope());
        return R.ok(map);
    }

    @Operation(summary = "登出(由 Security LogoutSuccessHandler 处理, 这里保留兼容)")
    @PostMapping("/logout")
    public R<Void> logout() {
        return R.ok();
    }

    @Operation(summary = "修改个人基本资料(昵称/邮箱/手机号)")
    @PutMapping("/profile")
    public R<Void> updateProfile(@RequestBody Map<String, String> body) {
        LoginUser u = SecurityUtils.getLoginUser();
        SysUser user = new SysUser();
        user.setUserId(u.getUserId());
        if (body.containsKey("nickName")) user.setNickName(body.get("nickName"));
        if (body.containsKey("email")) user.setEmail(body.get("email"));
        if (body.containsKey("phonenumber")) user.setPhonenumber(body.get("phonenumber"));
        return userService.updateUser(user) > 0 ? R.ok() : R.fail("修改失败");
    }

    @Operation(summary = "修改密码(验证旧密码)")
    @PutMapping("/pwd")
    public R<Void> updatePwd(@RequestBody Map<String, String> body) {
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        if (oldPwd == null || oldPwd.isBlank()) return R.fail("旧密码不能为空");
        if (newPwd == null || newPwd.length() < 6) return R.fail("新密码不能少于6位");
        LoginUser u = SecurityUtils.getLoginUser();
        SysUser db = userService.getById(u.getUserId());
        if (db == null || !SecurityUtils.matches(oldPwd, db.getPassword())) {
            return R.fail("旧密码不正确");
        }
        return userService.resetUserPwd(u.getUserId(), newPwd) > 0 ? R.ok() : R.fail("修改密码失败");
    }

    @Operation(summary = "验证码(占位:阶段一用前端图形验证码,后端未启用图形生成)")
    @GetMapping("/captcha")
    public R<Map<String, String>> captcha() {
        // TODO: 接入验证码工具(如 Easy-Captcha) 写 Redis, key = uuid, TTL=2min, 登录时校验
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String code = random4();
        Map<String, String> m = new HashMap<>(4);
        m.put("uuid", uuid);
        m.put("code", code);
        m.put("img", ""); // 阶段一图片验证码前端模拟
        return R.ok(m);
    }

    // ========= 辅助 =========
    private SysUser buildSimpleUser(LoginUser u) {
        SysUser s = new SysUser();
        s.setUserId(u.getUserId());
        s.setUserName(u.getUsername());
        s.setNickName(u.getNickName());
        s.setDeptId(u.getDeptId());
        return s;
    }

    private static String random4() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(4);
        Random r = new Random();
        for (int i = 0; i < 4; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }
}
