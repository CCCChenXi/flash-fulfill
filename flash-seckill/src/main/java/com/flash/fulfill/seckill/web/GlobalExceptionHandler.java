package com.flash.fulfill.seckill.web;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 秒杀服务全局异常处理。
 * <p>
 * 将参数校验(@Valid)失败、非法请求体、业务异常统一转换为 Result,保证接口返回一致格式。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("[BizException] code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse(ErrorCode.INVALID_PARAM.getMessage());
        return Result.fail(ErrorCode.INVALID_PARAM, msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("[HttpMessageNotReadableException] {}", e.getMessage());
        return Result.fail(ErrorCode.INVALID_PARAM, "请求体缺失或格式错误");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception e) {
        log.error("[UnknownException]", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }
}