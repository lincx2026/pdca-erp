package com.pcba.erp.sal.service;

import com.pcba.erp.sal.entity.SalKittingLine;

import java.util.List;

public interface ISalKittingLineService {
    List<SalKittingLine> listByKittingId(Long kittingId);
    Long save(SalKittingLine entity);
    boolean removeByKittingId(Long kittingId);
}
