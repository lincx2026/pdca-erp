package com.pcba.erp.common.exception;

import com.pcba.erp.common.core.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(ServiceException.class)
    public R<Void> handleServiceException(ServiceException e, HttpServletRequest req) {
        log.warn("业务异常 [{}] code={} msg={}", req.getRequestURI(), e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** 请求参数校验: @Valid @RequestBody */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return R.bad("参数校验失败: " + msg);
    }

    /** 请求参数绑定: @Valid @ModelAttribute */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getField() + ": " + fe.getDefaultMessage() : "参数绑定错误";
        log.warn("参数绑定失败: {}", msg);
        return R.bad("参数错误: " + msg);
    }

    /** 单参数校验 */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("单参数校验失败: {}", msg);
        return R.bad("参数错误: " + msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return R.bad("缺少必填参数: " + e.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleHttpMsgNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return R.bad("请求体格式错误(JSON解析失败)");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return R.fail(405, "请求方法不支持: " + e.getMethod());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public R<Void> handleNoFound(NoHandlerFoundException e) {
        return R.fail(404, "接口不存在: " + e.getRequestURL());
    }

    @ExceptionHandler(MyBatisSystemException.class)
    public R<Void> handleMybatis(MyBatisSystemException e, HttpServletRequest req) {
        log.error("数据库异常 [{}]", req.getRequestURI(), e);
        return R.fail(500, "数据库异常, 请检查参数或联系管理员");
    }

    /** 兜底 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleAll(Exception e, HttpServletRequest req) {
        log.error("系统异常 [{}]", req.getRequestURI(), e);
        return R.fail(500, "系统异常: " + e.getMessage());
    }
}
