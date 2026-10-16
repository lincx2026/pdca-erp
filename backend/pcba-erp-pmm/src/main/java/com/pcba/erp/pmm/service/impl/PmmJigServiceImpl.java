package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.event.NoticeEvent;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.PmmJig;
import com.pcba.erp.pmm.entity.PmmJigLog;
import com.pcba.erp.pmm.mapper.PmmJigLogMapper;
import com.pcba.erp.pmm.mapper.PmmJigMapper;
import com.pcba.erp.pmm.service.IPmmJigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PmmJigServiceImpl implements IPmmJigService {

    private final PmmJigMapper    jigMapper;
    private final PmmJigLogMapper logMapper;
    private final ApplicationEventPublisher eventPublisher;

    // ======================================================================================
    //                                      CRUD
    // ======================================================================================

    @Override
    public IPage<PmmJig> selectPage(int pageNum, int pageSize,
                                    String jigNo, String jigName, String jigType,
                                    String productCode, String productName, Integer status,
                                    String keeper, String warehouse, String location, String borrowerName) {
        return jigMapper.selectPage(new Page<>(pageNum, pageSize),
                jigNo, jigName, jigType, productCode, productName, status,
                keeper, warehouse, location, borrowerName);
    }

    @Override
    public PmmJig getDetail(Long id) {
        if (id == null) throw new ServiceException("工装ID不能为空");
        PmmJig j = jigMapper.selectById(id);
        if (j == null) throw new ServiceException("工装不存在");
        return j;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PmmJig e) {
        if (jigMapper.countDuplicateNo(e.getJigNo(), null) > 0)
            throw new ServiceException("工装编码【" + e.getJigNo() + "】已存在");
        defaultsForInsert(e);
        jigMapper.insert(e);

        // 自动补一条 INBOUND 入库日志(如果没有对应的入库流水)
        PmmJigLog in = new PmmJigLog();
        in.setJigId(e.getJigId());
        in.setJigNo(e.getJigNo());
        in.setEventType("INBOUND");
        in.setEventTitle(e.getJigName() + " 入库登记");
        if (e.getReceiveDate() != null) in.setEventTime(e.getReceiveDate().atStartOfDay());
        in.setEventResult("PASS");
        in.setBorrowerId(e.getKeeperId());
        in.setBorrowerName(e.getKeeperName());
        in.setCostAmount(e.getCostAmount());
        in.setRemark(e.getRemark());
        new com.pcba.erp.pmm.service.impl.PmmJigLogServiceImpl(logMapper, jigMapper).save(in);

        return e.getJigId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PmmJig e) {
        if (e.getJigId() == null) throw new ServiceException("工装ID不能为空");
        PmmJig old = jigMapper.selectById(e.getJigId());
        if (old == null) throw new ServiceException("工装不存在");
        if (e.getJigNo() != null && jigMapper.countDuplicateNo(e.getJigNo(), e.getJigId()) > 0)
            throw new ServiceException("工装编码已存在");
        return jigMapper.updateById(e) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        PmmJig j = jigMapper.selectById(id);
        if (j == null) throw new ServiceException("工装不存在");
        if (j.getStatus() == null || j.getStatus() != 6) {
            throw new ServiceException("只有【报废】状态的治具可以删除;请先执行【报废】流程或将 status 改为 报废(6)");
        }
        if (j.getQtyBorrowed() != null && j.getQtyBorrowed() > 0)
            throw new ServiceException("仍有【借出中】套数, 不能删除(借出=" + j.getQtyBorrowed() + "套)");
        return jigMapper.deleteById(id) > 0;
    }

    @Override
    public List<PmmJig> options(String productCode) {
        return jigMapper.listOptions(productCode);
    }

    // ======================================================================================
    //                              6 大业务动作(借用 JigLogServiceImpl helper)
    // ======================================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long borrow(Long jigId, PmmJigLog lg) {
        if (jigId == null) throw new ServiceException("工装ID不能为空");
        PmmJig j = jigMapper.selectById(jigId);
        if (j == null) throw new ServiceException("工装不存在");
        // 寿命 100% 的禁止再次借出
        if (j.getLifeCount() != null && j.getLifeCount() > 0 &&
                j.getUsedCount() != null && j.getUsedCount() >= j.getLifeCount()) {
            throw new ServiceException("该治具已达到寿命阈值("
                    + j.getUsedCount() + "/" + j.getLifeCount() + "次), 请先【修磨/更换】后再借用");
        }
        if (lg == null) lg = new PmmJigLog();
        lg.setJigId(jigId);
        lg.setEventType("BORROW");
        if (lg.getEventTitle() == null || lg.getEventTitle().isBlank())
            lg.setEventTitle(j.getJigName() + " 领用登记");
        if (lg.getEventResult() == null) lg.setEventResult("PASS");
        // 1) 联动主表
        PmmJigLogServiceImpl.doBorrow(j, lg);
        jigMapper.updateById(j);
        // 2) 记日志
        lg.setJigNo(j.getJigNo());
        lg.setEventTime(lg.getEventTime() == null ? java.time.LocalDateTime.now() : lg.getEventTime());
        if (lg.getCostAmount() == null) lg.setCostAmount(BigDecimal.ZERO);
        logMapper.insert(lg);
        // 自动通知：治具领用
        eventPublisher.publishEvent(NoticeEvent.builder()
                .noticeType(2).noticeLevel(1)
                .title("治具 " + j.getJigNo() + " 已被领用")
                .summary(j.getJigName() + " → " + (lg.getBorrowerName() != null ? lg.getBorrowerName() : "领用人"))
                .content("<p>治具 <b>" + j.getJigNo() + " " + j.getJigName() + "</b> 已被 <b>" + (lg.getBorrowerName() != null ? lg.getBorrowerName() : "领用人") + "</b> 领用。</p>")
                .relatedType("JIG").relatedId(String.valueOf(jigId)).actionRoute("/pm/jig").build());
        // 寿命预警：使用次数超过阈值90%
        if (j.getLifeCount() != null && j.getLifeCount() > 0 && j.getUsedCount() != null) {
            int pct = j.getUsedCount() * 100 / j.getLifeCount();
            if (pct >= 90) {
                eventPublisher.publishEvent(NoticeEvent.builder()
                        .noticeType(3).noticeLevel(3)
                        .title("治具 " + j.getJigNo() + " 寿命预警：" + pct + "%")
                        .summary(j.getJigName() + " 已使用 " + j.getUsedCount() + "/" + j.getLifeCount() + " 次")
                        .content("<p>治具 <b>" + j.getJigNo() + " " + j.getJigName() + "</b> 已使用 " + j.getUsedCount() + "/" + j.getLifeCount() + " 次（" + pct + "%），请安排修磨或更换。</p>")
                        .relatedType("JIG").relatedId(String.valueOf(jigId)).actionRoute("/pm/jig").build());
            }
        }
        return lg.getLogId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long returnBack(Long jigId, PmmJigLog lg) {
        if (jigId == null) throw new ServiceException("工装ID不能为空");
        PmmJig j = jigMapper.selectById(jigId);
        if (j == null) throw new ServiceException("工装不存在");
        if (lg == null) lg = new PmmJigLog();
        lg.setJigId(jigId);
        lg.setEventType("RETURN");
        if (lg.getEventTitle() == null || lg.getEventTitle().isBlank())
            lg.setEventTitle(j.getJigName() + " 归还登记");
        if (lg.getEventResult() == null) lg.setEventResult("PASS");
        if (lg.getUsedCountAdd() == null) lg.setUsedCountAdd(0);
        if (lg.getCostAmount() == null) lg.setCostAmount(BigDecimal.ZERO);
        lg.setEventTime(lg.getEventTime() == null ? java.time.LocalDateTime.now() : lg.getEventTime());
        // 1) 联动主表
        PmmJigLogServiceImpl.doReturn(j, lg);
        jigMapper.updateById(j);
        // 2) 记日志
        lg.setJigNo(j.getJigNo());
        logMapper.insert(lg);
        return lg.getLogId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long calibrate(Long jigId, PmmJigLog lg) {
        PmmJig j = jig(jigId);
        if (lg == null) lg = new PmmJigLog();
        lg.setJigId(jigId);
        lg.setEventType("CALIBRATE");
        if (lg.getEventTitle() == null || lg.getEventTitle().isBlank())
            lg.setEventTitle(j.getJigName() + " 校准/张力测试");
        if (lg.getEventResult() == null) lg.setEventResult("PASS");
        lg.setEventTime(lg.getEventTime() == null ? java.time.LocalDateTime.now() : lg.getEventTime());
        if (lg.getCostAmount() == null) lg.setCostAmount(BigDecimal.ZERO);
        // 开始校准前 → status=3校准中
        j.setStatus(3);
        jigMapper.updateById(j);
        PmmJigLogServiceImpl.doCalibrate(j, lg);
        jigMapper.updateById(j);
        lg.setJigNo(j.getJigNo());
        logMapper.insert(lg);
        // 自动通知：治具校准完成
        eventPublisher.publishEvent(NoticeEvent.builder()
                .noticeType(2).noticeLevel(1)
                .title("治具 " + j.getJigNo() + " 校准完成")
                .summary(j.getJigName() + " 结果:" + (lg.getEventResult() != null ? lg.getEventResult() : "PASS"))
                .content("<p>治具 <b>" + j.getJigNo() + " " + j.getJigName() + "</b> 校准完成，结果：" + (lg.getEventResult() != null ? lg.getEventResult() : "PASS") + "。</p>")
                .relatedType("JIG").relatedId(String.valueOf(jigId)).actionRoute("/pm/jig").build());
        return lg.getLogId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long resharpen(Long jigId, PmmJigLog lg) {
        PmmJig j = jig(jigId);
        if (lg == null) lg = new PmmJigLog();
        lg.setJigId(jigId);
        lg.setEventType("RESHARPEN");
        if (lg.getEventTitle() == null || lg.getEventTitle().isBlank())
            lg.setEventTitle(j.getJigName() + " 修磨/更换钢片/更换PIN/海绵板");
        if (lg.getEventResult() == null) lg.setEventResult("PASS");
        lg.setEventTime(lg.getEventTime() == null ? java.time.LocalDateTime.now() : lg.getEventTime());
        if (lg.getCostAmount() == null) lg.setCostAmount(BigDecimal.ZERO);
        j.setStatus(4);
        jigMapper.updateById(j);
        PmmJigLogServiceImpl.doResharpen(j, lg);
        jigMapper.updateById(j);
        lg.setJigNo(j.getJigNo());
        logMapper.insert(lg);
        return lg.getLogId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long repair(Long jigId, PmmJigLog lg) {
        PmmJig j = jig(jigId);
        if (lg == null) lg = new PmmJigLog();
        lg.setJigId(jigId);
        lg.setEventType("REPAIR");
        if (lg.getEventTitle() == null || lg.getEventTitle().isBlank())
            lg.setEventTitle(j.getJigName() + " 维修工单执行");
        if (lg.getEventResult() == null) lg.setEventResult("PASS");
        lg.setEventTime(lg.getEventTime() == null ? java.time.LocalDateTime.now() : lg.getEventTime());
        if (lg.getCostAmount() == null) lg.setCostAmount(BigDecimal.ZERO);
        j.setStatus(5); // 开始维修
        jigMapper.updateById(j);
        PmmJigLogServiceImpl.doRepair(j, lg);
        jigMapper.updateById(j);
        lg.setJigNo(j.getJigNo());
        logMapper.insert(lg);
        return lg.getLogId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long scrap(Long jigId, PmmJigLog lg) {
        PmmJig j = jig(jigId);
        if (j.getQtyCurrent() == null || j.getQtyCurrent() <= 0)
            throw new ServiceException("该工装总数为0, 已无法再报废");
        if (j.getStatus() != null && j.getStatus() == 2 && j.getQtyBorrowed() != null && j.getQtyBorrowed() > 0)
            throw new ServiceException("该工装当前还有【借出中】套数, 必须先归还后才能报废(借出=" + j.getQtyBorrowed() + "套)");
        if (lg == null) lg = new PmmJigLog();
        lg.setJigId(jigId);
        lg.setEventType("SCRAP");
        if (lg.getEventTitle() == null || lg.getEventTitle().isBlank())
            lg.setEventTitle(j.getJigName() + " 报废(磨损超限/不可修复)");
        if (lg.getEventResult() == null) lg.setEventResult("PASS");
        lg.setEventTime(lg.getEventTime() == null ? java.time.LocalDateTime.now() : lg.getEventTime());
        if (lg.getCostAmount() == null) lg.setCostAmount(BigDecimal.ZERO);
        PmmJigLogServiceImpl.doScrap(j, lg);
        jigMapper.updateById(j);
        lg.setJigNo(j.getJigNo());
        logMapper.insert(lg);
        // 自动通知：治具报废（紧急）
        eventPublisher.publishEvent(NoticeEvent.builder()
                .noticeType(3).noticeLevel(3)
                .title("治具 " + j.getJigNo() + " 已报废")
                .summary(j.getJigName() + " (" + j.getJigNo() + ")")
                .content("<p>治具 <b>" + j.getJigNo() + " " + j.getJigName() + "</b> 已执行报废处理，请安排补充。</p>")
                .relatedType("JIG").relatedId(String.valueOf(jigId)).actionRoute("/pm/jig").build());
        return lg.getLogId();
    }

    // ======================================================================================
    //                                    Helper
    // ======================================================================================

    private PmmJig jig(Long id) {
        if (id == null) throw new ServiceException("工装ID不能为空");
        PmmJig j = jigMapper.selectById(id);
        if (j == null) throw new ServiceException("工装不存在");
        return j;
    }

    private void defaultsForInsert(PmmJig e) {
        if (e.getStatus() == null) e.setStatus(0);
        if (e.getUsedCount() == null) e.setUsedCount(0);
        if (e.getLifeCount() == null || e.getLifeCount() < 0) e.setLifeCount(0);
        if (e.getQtyCurrent() == null || e.getQtyCurrent() < 1) e.setQtyCurrent(1);
        if (e.getQtyAvailable() == null) e.setQtyAvailable(e.getQtyCurrent());
        if (e.getQtyBorrowed() == null) e.setQtyBorrowed(0);
        if (e.getCostAmount() == null) e.setCostAmount(BigDecimal.ZERO);
        if (e.getCalCycleDays() != null && e.getCalCycleDays() > 0) {
            if (e.getNextCalDate() == null) {
                java.time.LocalDate d = e.getReceiveDate() != null ? e.getReceiveDate() : java.time.LocalDate.now();
                e.setNextCalDate(d.plusDays(e.getCalCycleDays()));
            }
        }
    }
}
