package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.CompanyCodingRule;
import com.pcba.erp.pmm.mapper.CompanyCodingRuleMapper;
import com.pcba.erp.pmm.service.IPmmCompanyCodingRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 编码规则 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyCodingRuleServiceImpl implements IPmmCompanyCodingRuleService {

    private final CompanyCodingRuleMapper ruleMapper;

    @Override
    public List<CompanyCodingRule> listAll() {
        return ruleMapper.selectAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(CompanyCodingRule entity) {
        if (entity == null) throw new ServiceException("编码规则不能为空");
        if (entity.getModuleName() == null || entity.getModuleName().isBlank())
            throw new ServiceException("模块名称不能为空");
        if (entity.getRuleId() == null) {
            if (entity.getCurrentSeq() == null) entity.setCurrentSeq(0);
            if (entity.getStatus() == null) entity.setStatus(0);
            ruleMapper.insert(entity);
        } else {
            ruleMapper.updateById(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        if (id == null) throw new ServiceException("编码规则ID不能为空");
        CompanyCodingRule rule = ruleMapper.selectById(id);
        if (rule == null) throw new ServiceException("编码规则不存在");
        ruleMapper.deleteById(id);
    }

    /**
     * 根据规则生成编码: prefix + separator + 日期(如有) + separator + 零填充流水号
     * 生成后 currentSeq+1 并更新数据库
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateCode(String moduleName) {
        if (moduleName == null || moduleName.isBlank())
            throw new ServiceException("模块名称不能为空");
        CompanyCodingRule rule = ruleMapper.selectOne(
                new LambdaQueryWrapper<CompanyCodingRule>()
                        .eq(CompanyCodingRule::getModuleName, moduleName));
        if (rule == null) throw new ServiceException("未找到模块【" + moduleName + "】的编码规则");
        if (rule.getStatus() != null && rule.getStatus() == 1)
            throw new ServiceException("模块【" + moduleName + "】的编码规则已停用");

        String prefix = rule.getPrefix() == null ? "" : rule.getPrefix();
        String sep = rule.getSeparator() == null ? "" : rule.getSeparator();
        int seqLen = rule.getSeqLength() != null && rule.getSeqLength() > 0 ? rule.getSeqLength() : 4;
        int nextSeq = (rule.getCurrentSeq() == null ? 0 : rule.getCurrentSeq()) + 1;
        String seqStr = String.format("%0" + seqLen + "d", nextSeq);

        StringBuilder code = new StringBuilder(prefix);
        String dateFmt = rule.getDateFormat();
        if (dateFmt != null && !dateFmt.isBlank()) {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern(dateFmt));
            code.append(sep).append(dateStr);
        }
        code.append(sep).append(seqStr);

        // 更新流水号
        rule.setCurrentSeq(nextSeq);
        ruleMapper.updateById(rule);

        return code.toString();
    }
}
