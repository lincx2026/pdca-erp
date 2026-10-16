package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.PmProject;
import com.pcba.erp.pmm.mapper.PmProjectMapper;
import com.pcba.erp.pmm.service.IPmProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class PmProjectServiceImpl implements IPmProjectService {

    private final PmProjectMapper mapper;
    private final StringRedisTemplate redisTemplate;

    /** Lua 脚本：原子 INCR + 首次设置过期 */
    private static final String LUA_CODE_INCR =
            "local v = redis.call('INCR', KEYS[1]) " +
            "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return v";

    @Override
    public IPage<PmProject> selectPage(int pageNum, int pageSize,
                                       String projectCode, String projectName,
                                       String customerName, String manager,
                                       Long categoryId,
                                       Integer status, String priority) {
        return mapper.selectPage(new Page<>(pageNum, pageSize),
                projectCode, projectName, customerName, manager, categoryId, status, priority);
    }

    @Override
    public PmProject getDetail(Long id) {
        if (id == null) throw new ServiceException("项目ID不能为空");
        PmProject p = mapper.selectById(id);
        if (p == null) throw new ServiceException("项目不存在");
        return p;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PmProject entity) {
        if (entity.getProjectName() == null || entity.getProjectName().trim().isEmpty())
            throw new ServiceException("项目名称不能为空");
        // 编号为空则自动生成
        if (entity.getProjectCode() == null || entity.getProjectCode().trim().isEmpty())
            entity.setProjectCode(nextProjectCode());
        if (entity.getStatus() == null) entity.setStatus(0);
        if (entity.getPriority() == null || entity.getPriority().trim().isEmpty()) entity.setPriority("M");
        if (entity.getBudget() == null) entity.setBudget(BigDecimal.ZERO);
        mapper.insert(entity);
        return entity.getProjectId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PmProject entity) {
        if (entity.getProjectId() == null) throw new ServiceException("项目ID不能为空");
        PmProject old = mapper.selectById(entity.getProjectId());
        if (old == null) throw new ServiceException("项目不存在");
        // 已完成/已取消的项目不允许修改
        if (old.getStatus() != null && (old.getStatus() == 2 || old.getStatus() == 3))
            throw new ServiceException("已完成或已取消的项目不允许修改");
        // 编号不允许修改
        entity.setProjectCode(old.getProjectCode());
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        if (id == null) throw new ServiceException("项目ID不能为空");
        PmProject old = mapper.selectById(id);
        if (old == null) throw new ServiceException("项目不存在");
        if (old.getStatus() != null && old.getStatus() == 1)
            throw new ServiceException("进行中的项目不允许删除，请先取消");
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer targetStatus) {
        if (id == null) throw new ServiceException("项目ID不能为空");
        if (targetStatus == null) throw new ServiceException("目标状态不能为空");
        PmProject old = mapper.selectById(id);
        if (old == null) throw new ServiceException("项目不存在");

        Integer cur = old.getStatus();
        // 状态流转校验
        boolean valid = false;
        if (targetStatus == 1 && cur == 0) valid = true;                    // 待启动 -> 进行中
        else if (targetStatus == 2 && cur == 1) valid = true;               // 进行中 -> 已完成
        else if (targetStatus == 3 && (cur == 0 || cur == 1)) valid = true; // 待启动/进行中 -> 已取消
        if (!valid)
            throw new ServiceException("不允许从状态 " + cur + " 流转到 " + targetStatus);

        PmProject update = new PmProject();
        update.setProjectId(id);
        update.setStatus(targetStatus);
        if (targetStatus == 2) {
            // 完成时自动写入实际完成日期
            update.setActualEndDate(LocalDate.now());
        }
        return mapper.updateById(update) > 0;
    }

    /**
     * 项目编号: PRJ + yyyyMMdd + 4位序号 (Redis Lua 原子 INCR, 按天滚动, 2天过期)
     */
    @Override
    public String nextProjectCode() {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String seqKey = "pm:project:code:seq:" + today;
        long expireSec = 2L * 24 * 3600;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_CODE_INCR, Long.class);
        Long seq = redisTemplate.execute(script, Collections.singletonList(seqKey), String.valueOf(expireSec));
        if (seq == null) throw new ServiceException("项目编号生成失败");
        return "PRJ" + today + String.format("%04d", seq);
    }
}
