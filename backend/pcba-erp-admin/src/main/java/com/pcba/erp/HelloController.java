package com.pcba.erp;

import com.pcba.erp.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查 + 系统信息接口(冒烟测试用)
 */
@Tag(name = "系统信息", description = "冒烟测试/健康检查接口")
@RestController
@RequestMapping("/system")
public class HelloController {

    @Operation(summary = "Hello World 冒烟测试")
    @GetMapping("/hello")
    public R<Map<String, Object>> hello() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("app",       "PCBA ERP 自研系统");
        info.put("version",   "1.0.0");
        info.put("timestamp", LocalDateTime.now());
        info.put("modules",   new String[] {"system(权限)", "pdm(产品/物料/BOM)"});
        return R.ok("PCBA ERP 后端骨架启动成功 🚀", info);
    }

    @Operation(summary = "Ping 接口")
    @GetMapping("/ping")
    public R<String> ping() {
        return R.ok("pong");
    }
}
