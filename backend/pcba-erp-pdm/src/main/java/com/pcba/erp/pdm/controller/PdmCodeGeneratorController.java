package com.pcba.erp.pdm.controller;

import com.pcba.erp.common.core.R;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.entity.PdmMaterialCategory;
import com.pcba.erp.pdm.mapper.PdmMaterialCategoryMapper;
import com.pcba.erp.pdm.mapper.PdmMaterialMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.Collections;
import java.util.List;

/**
 * 物料编码生成器 Controller
 *
 * 编码规则: {category_code}-{YY}-{4位流水}
 *   例: RES-26-0001 / IC-26-0123 / PCB-26-0099
 *   - 流水号按 {分类编码 + 年份} 维度独立计数
 *   - 使用 Redis Lua 脚本保证原子自增 + 防止并发撞码
 *   - 返回前再查 DB 双重校验 (防止 Redis 重启后重置)
 */
@Slf4j
@Tag(name = "物料编码生成器")
@RestController
@RequestMapping("/pdm/code")
@RequiredArgsConstructor
public class PdmCodeGeneratorController {

    private static final String REDIS_PREFIX = "pdm:code:seq:";

    private final StringRedisTemplate redisTemplate;
    private final PdmMaterialCategoryMapper categoryMapper;
    private final PdmMaterialMapper materialMapper;

    /**
     * Lua 脚本: 原子 INCR + 设置过期 (到下一年年底 + 30 天)
     *   KEYS[1] = redis key
     *   ARGV[1] = expire seconds
     * 返回: 自增后的新值
     */
    private static final String LUA_INCR =
            "local v = redis.call('INCR', KEYS[1]) " +
            "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return v";

    /**
     * 预生成 next 序列 (不保存 DB, 只返回给前端表单填入)
     *
     * @param categoryId 分类ID, 用于取 category_code
     * @param count      一次生成几个 (默认1), 批量新建场景用
     */
    @Operation(summary = "生成下一个物料编码 (不入库, 用于表单回填)")
    @PreAuthorize("@ps.hasPermi('pdm:material:add')")
    @GetMapping("/generate")
    public R<List<String>> generate(@RequestParam Long categoryId,
                                    @RequestParam(required = false, defaultValue = "1") int count) {
        if (count <= 0 || count > 50) return R.fail("生成数量 1~50");
        PdmMaterialCategory cat = categoryMapper.selectById(categoryId);
        if (cat == null) return R.fail("分类不存在");
        String prefix = cat.getCategoryCode();
        if (prefix == null || prefix.isBlank()) return R.fail("分类编码前缀未配置");

        int yy = Year.now().getValue() % 100;   // 2026 → 26
        String seqKey = REDIS_PREFIX + prefix + ":" + yy;
        // 过期秒数: 到明年年底 + 30 天 (至少 1 年, 覆盖次年还在用旧年度编码场景)
        long expireSec = 366L * 24 * 3600 + 30L * 24 * 3600;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_INCR, Long.class);
        String[] codes = new String[count];
        for (int i = 0; i < count; i++) {
            Long seq = redisTemplate.execute(script, Collections.singletonList(seqKey), String.valueOf(expireSec));
            if (seq == null) throw new ServiceException("编码生成失败, Redis 返回空");
            // DB 去重兜底: 如果已存在就继续 +1
            String candidate;
            int guard = 0;
            do {
                candidate = buildCode(prefix, yy, seq);
                if (materialMapper.countDuplicateCode(candidate, null) == 0) break;
                seq = redisTemplate.execute(script, Collections.singletonList(seqKey), String.valueOf(expireSec));
                if (++guard > 1000) throw new ServiceException("编码冲突过多, 请联系管理员");
            } while (true);
            codes[i] = candidate;
        }
        return R.ok(java.util.Arrays.asList(codes));
    }

    /**
     * 前端输入一个分类编码前缀, 给出预览 (不占用号段, 不执行自增)
     */
    @Operation(summary = "预览编码格式 (不占用号段)")
    @PreAuthorize("@ps.hasPermi('pdm:category:list')")
    @GetMapping("/preview")
    public R<String> preview(@RequestParam Long categoryId) {
        PdmMaterialCategory cat = categoryMapper.selectById(categoryId);
        if (cat == null) return R.fail("分类不存在");
        int yy = Year.now().getValue() % 100;
        return R.ok(buildCode(cat.getCategoryCode(), yy, 1L) + " ~ " + buildCode(cat.getCategoryCode(), yy, 9999L));
    }

    private String buildCode(String prefix, int yy, long seq) {
        return prefix.toUpperCase() + "-" + String.format("%02d", yy) + "-" + String.format("%04d", seq);
    }
}
