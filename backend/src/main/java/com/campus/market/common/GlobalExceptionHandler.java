package com.campus.market.common;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理（03§3.6.3）：
 * 参数校验失败 → A0400 + 字段级明细；业务异常 → 对应错误码；其余 → C0500 + traceId。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("参数非法");
        log.warn("validation failed: {}", detail);
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.BAD_REQUEST, detail));
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        log.warn("biz error [{}]: {}", e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity.status(e.getErrorCode().getHttpStatus())
                .body(Result.fail(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        String traceId = MDC.get("traceId");
        log.error("internal error traceId={}", traceId, e);
        return ResponseEntity.internalServerError().body(Result.fail(ErrorCode.INTERNAL_ERROR));
    }
}