package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.scm.entity.ScmApPayableLine;
import com.pcba.erp.scm.mapper.ScmApPayableLineMapper;
import com.pcba.erp.scm.service.IScmApPayableLineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScmApPayableLineServiceImpl implements IScmApPayableLineService {

    private final ScmApPayableLineMapper mapper;

    @Override
    public IPage<ScmApPayableLine> selectPage(int pageNum, int pageSize, Long payableId, String materialCode, String materialName) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), payableId, materialCode, materialName);
    }

    @Override
    public ScmApPayableLine getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        ScmApPayableLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScmApPayableLine entity) {
        // lineNo 兜底：按主单max(lineNo)+10
        if (entity.getLineNo() == null && entity.getPayableId() != null) {
            Integer max = null;
            try { max = mapper.selectOne(new LambdaQueryWrapper<ScmApPayableLine>()
                    .select(ScmApPayableLine::getLineNo)
                    .eq(ScmApPayableLine::getPayableId, entity.getPayableId())
                    .orderByDesc(ScmApPayableLine::getLineNo).last("LIMIT 1")).getLineNo(); } catch (Exception ignore){}
            entity.setLineNo(max == null ? 10 : max + 10);
        }
        for (String f : new String[]{"qty","unitPrice","taxRate","undiscAmount","taxAmount","lineAmount"}) {
            try {
                Field ff = ScmApPayableLine.class.getDeclaredField(f);
                ff.setAccessible(true);
                if (ff.get(entity) == null && ff.getType() == BigDecimal.class) ff.set(entity, BigDecimal.ZERO);
            } catch (Exception ignore){}
        }
        // 金额兜底
        BigDecimal qty = entity.getQty() == null ? BigDecimal.ZERO : entity.getQty();
        BigDecimal up  = entity.getUnitPrice() == null ? BigDecimal.ZERO : entity.getUnitPrice();
        BigDecimal tr  = entity.getTaxRate() == null ? BigDecimal.ZERO : entity.getTaxRate();
        if ((entity.getUndiscAmount() == null || BigDecimal.ZERO.compareTo(entity.getUndiscAmount()) == 0)
                && (BigDecimal.ZERO.compareTo(qty) != 0 || BigDecimal.ZERO.compareTo(up) != 0)) {
            entity.setUndiscAmount(qty.multiply(up));
        }
        if (entity.getTaxAmount() == null || BigDecimal.ZERO.compareTo(entity.getTaxAmount()) == 0) {
            entity.setTaxAmount(entity.getUndiscAmount().multiply(tr));
        }
        if (entity.getLineAmount() == null || BigDecimal.ZERO.compareTo(entity.getLineAmount()) == 0) {
            entity.setLineAmount(entity.getUndiscAmount().add(entity.getTaxAmount()));
        }
        mapper.insert(entity);
        return entity.getLineId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ScmApPayableLine entity) {
        if (entity.getLineId() == null) throw new ServiceException("ID不能为空");
        ScmApPayableLine old = mapper.selectById(entity.getLineId());
        if (old == null) throw new ServiceException("记录不存在");
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        ScmApPayableLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<ScmApPayableLine> listByPayable(Long payableId) {
        return mapper.selectList(new LambdaQueryWrapper<ScmApPayableLine>()
                .eq(ScmApPayableLine::getPayableId, payableId).orderByAsc(ScmApPayableLine::getLineNo));
    }
}
