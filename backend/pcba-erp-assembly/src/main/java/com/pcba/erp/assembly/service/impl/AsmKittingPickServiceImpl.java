package com.pcba.erp.assembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.assembly.entity.AsmKittingPick;
import com.pcba.erp.assembly.mapper.AsmKittingPickMapper;
import com.pcba.erp.assembly.service.IAsmKittingPickService;
import com.pcba.erp.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 拣货记录 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsmKittingPickServiceImpl implements IAsmKittingPickService {

    private final AsmKittingPickMapper pickMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public IPage<AsmKittingPick> selectPage(int pageNum, int pageSize,
                                            Long kittingId, Long materialId, Integer pickStatus) {
        Page<AsmKittingPick> page = new Page<>(pageNum, pageSize);
        return pickMapper.selectPage(page, kittingId, materialId, pickStatus);
    }

    @Override
    public AsmKittingPick getDetail(Long id) {
        AsmKittingPick p = pickMapper.selectById(id);
        if (p == null) throw new ServiceException("拣货记录不存在");
        return p;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(AsmKittingPick entity) {
        if (entity.getPickStatus() == null) entity.setPickStatus(0); // 待拣
        if (entity.getPickNo() == null || entity.getPickNo().isEmpty()) {
            entity.setPickNo(nextPickNo());
        } else {
            int dup = pickMapper.countDuplicate(entity.getPickNo(), null);
            if (dup > 0) throw new ServiceException("拣货单号【" + entity.getPickNo() + "】已存在");
        }
        if (pickMapper.insert(entity) <= 0) throw new ServiceException("新增拣货记录失败");
        return entity.getPickId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(AsmKittingPick entity) {
        if (entity.getPickId() == null) throw new ServiceException("拣货记录ID不能为空");
        AsmKittingPick old = pickMapper.selectById(entity.getPickId());
        if (old == null) throw new ServiceException("拣货记录不存在");
        if (entity.getPickNo() != null) {
            int dup = pickMapper.countDuplicate(entity.getPickNo(), entity.getPickId());
            if (dup > 0) throw new ServiceException("拣货单号已存在");
        }
        return pickMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        AsmKittingPick p = pickMapper.selectById(id);
        if (p == null) throw new ServiceException("拣货记录不存在");
        return pickMapper.deleteById(id) > 0;
    }

    @Override
    public List<AsmKittingPick> options() {
        return pickMapper.selectList(
                new LambdaQueryWrapper<AsmKittingPick>()
                        .orderByDesc(AsmKittingPick::getPickId)
                        .last("LIMIT 200"));
    }

    @Override
    public String nextPickNo() {
        String date = LocalDateTime.now().format(FMT);
        String prefix = "PK-" + date + "-";
        AsmKittingPick last = pickMapper.selectOne(
                new LambdaQueryWrapper<AsmKittingPick>()
                        .likeRight(AsmKittingPick::getPickNo, prefix)
                        .orderByDesc(AsmKittingPick::getPickId)
                        .last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getPickNo() != null) {
            try {
                String tail = last.getPickNo().substring(prefix.length());
                seq = Integer.parseInt(tail) + 1;
            } catch (Exception ignored) { /* 默认 1 */ }
        }
        return prefix + String.format("%04d", seq);
    }
}
