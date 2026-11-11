package com.campus.market.common;

import lombok.Getter;

/**
 * 业务异常：Service 层抛出，由 GlobalExceptionHandler 统一转换为 Result。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}