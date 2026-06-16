package com.wos.exception;

import com.wos.common.ResultCode;
import lombok.Getter;

/**
 * 业务异常。
 * 业务校验失败时主动抛出,由 {@link GlobalExceptionHandler} 统一捕获并转换为标准响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务状态码,取值见 {@link ResultCode} */
    private final Integer code;

    /**
     * 仅指定提示信息,状态码默认为 {@link ResultCode#CONFLICT}(409)。
     */
    public BusinessException(String message) {
        this(ResultCode.CONFLICT.getCode(), message);
    }

    /**
     * 使用 {@link ResultCode} 自带的状态码与提示信息。
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 使用 {@link ResultCode} 的状态码,并自定义提示信息。
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /**
     * 直接指定状态码与提示信息。
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
