package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.PmmEquipment;

import java.util.List;

public interface IPmmEquipmentService {

    IPage<PmmEquipment> selectPage(int pageNum, int pageSize, String equipmentNo, String equipmentName,
                                    String equipmentType, Integer status, Integer equipLevel,
                                    String workshop, String workCenter);

    PmmEquipment getDetail(Long id);

    Long save(PmmEquipment entity);

    boolean update(PmmEquipment entity);

    boolean remove(Long id);

    /** 设备下拉列表 (ASM/SN/排产 下拉用) */
    List<PmmEquipment> options();
}
