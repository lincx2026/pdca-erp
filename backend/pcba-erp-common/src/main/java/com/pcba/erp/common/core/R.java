package com.pcba.erp.common.core;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应对象
 *
 * @param <T> 数据泛型
 */
@Data
@NoArgsConstructor
@Schema(title = "统一响应对象")
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码: 200=成功, 其他=失败 */
    @Schema(description = "状态码: 200=成功, 500=通用失败, 401=未登录, 403=无权限, 400=参数错误")
    private int code;

    /** 返回消息 */
    @Schema(description = "返回消息")
    private String msg;

    /** 数据体 */
    @Schema(description = "数据体")
    private T data;

    /** 时间戳 */
    @Schema(description = "服务端时间戳(ms)")
    private long timestamp = System.currentTimeMillis();

    private R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(200, "操作成功", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(200, "操作成功", data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(200, msg, data);
    }

    public static <T> R<T> fail() {
        return new R<>(500, "操作失败", null);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(500, msg, null);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }

    public static <T> R<T> bad(String msg) {
        return new R<>(400, msg, null);
    }

    public static <T> R<T> unauthorized(String msg) {
        return new R<>(401, msg, null);
    }

    public static <T> R<T> forbidden(String msg) {
        return new R<>(403, msg, null);
    }

    public boolean isSuccess() {
        return this.code == 200;
    }
}
