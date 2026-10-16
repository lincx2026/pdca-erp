package com.pcba.erp.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 操作日志事件(异步落库不阻塞主流程) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperLogEvent {
    private String title;           // 模块
    private Integer businessType;   // 业务类型
    private String method;          // 方法类名#方法名
    private String requestMethod;   // GET/POST/...
    private String operName;        // 操作人
    private String deptName;        // 操作人部门
    private String operUrl;         // 请求URL
    private String operIp;          // 操作IP
    private String operLocation;    // 位置(未实现,留空)
    private String operParam;       // 请求参数 JSON
    private String jsonResult;      // 响应 JSON
    private Integer status;         // 0=成功 1=失败
    private String errorMsg;        // 错误信息
    private LocalDateTime operTime; // 操作时间
    private Long costTime;          // 耗时(ms)
}
