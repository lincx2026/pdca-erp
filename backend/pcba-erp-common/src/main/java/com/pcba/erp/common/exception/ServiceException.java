package com.pcba.erp.common.exception;

import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class ServiceException extends RuntimeException {

    private final int code;

    public ServiceException(String msg) {
        super(msg);
        this.code = 500;
    }

    public ServiceException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public static ServiceException of(String msg) {
        return new ServiceException(msg);
    }

    public static ServiceException of(int code, String msg) {
        return new ServiceException(code, msg);
    }
}
