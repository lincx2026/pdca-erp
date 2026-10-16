package com.pcba.erp.common.core.page;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 前端分页响应 (兼容 Element Plus el-pagination: 一般传 total/rows 两个字段)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableDataInfo<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    private long total = 0;
    private List<T> rows = Collections.emptyList();
    private String msg = "success";
    private int code = 200;

    public TableDataInfo(List<T> list, long total) {
        this.rows = list == null ? Collections.emptyList() : list;
        this.total = total;
    }

    public static <T> TableDataInfo<T> of(List<T> list, long total) {
        return new TableDataInfo<>(list, total);
    }
}
