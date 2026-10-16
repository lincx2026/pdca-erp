package com.pcba.erp.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 操作日志 sys_oper_log */
@Data
@TableName("sys_oper_log")
public class SysOperLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long operId;
    private String title;
    /** 业务类型 0其它/1新增/2修改/3删除/4授权/5导出/6导入/... */
    private Integer businessType;
    private String method;
    private String requestMethod;
    /** 操作类别 0其它 1后台 2手机 */
    private Integer operatorType;
    private String operName;
    private String deptName;
    private String operUrl;
    private String operIp;
    private String operLocation;
    private String operParam;
    private String jsonResult;
    /** 0=成功 1=失败 */
    private Integer status;
    private String errorMsg;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operTime;
    /** 耗时(ms) */
    private Long costTime;
}
