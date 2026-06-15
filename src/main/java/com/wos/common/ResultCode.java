package com.wos.common;

import lombok.Getter;

/**
 * 业务状态码。
 * <p>
 * 写入响应体的 code 字段,与 HTTP 状态码相互独立。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "数据不存在"),
    CONFLICT(409, "业务冲突"),
    INTERNAL_SERVER_ERROR(500, "系统异常,请联系管理员");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
