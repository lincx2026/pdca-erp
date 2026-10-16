package com.pcba.erp.scm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmSupplier;

import java.util.List;

public interface IScmSupplierService {
    IPage<ScmSupplier> selectPage(int pageNum, int pageSize, String supplierCode, String supplierName, String contact, String phone, String level, Integer status);
    ScmSupplier getDetail(Long id);
    Long save(ScmSupplier entity);
    boolean update(ScmSupplier entity);
    boolean remove(Long id);
    boolean changeStatus(Long id, Integer status);
    List<ScmSupplier> options();

    /** 生成下一个供应商编码: SUP + yyyyMMdd + 4位序号 */
    String nextSupplierCode();
}
