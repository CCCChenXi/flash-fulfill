package com.flash.fulfill.common.api;

/**
 * 全局错误码。
 * 200 成功;4xx 客户端错误;5xxx 服务端/业务错误。
 */
public enum ErrorCode {

    SUCCESS(200, "成功"),

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),

    INVALID_PARAM(10001, "参数校验失败"),
    STOCK_NOT_ENOUGH(10002, "库存不足"),
    ORDER_ALREADY_EXISTS(10003, "订单已存在(重复请求)"),
    DEDUCT_FAILED(10004, "库存扣减失败"),
    ORDER_NOT_FOUND(10005, "订单不存在"),
    GATEWAY_AUTH_FAILED(10006, "网关鉴权失败"),
    USER_ALREADY_EXISTS(10007, "用户名已存在"),
    INVALID_CREDENTIALS(10008, "用户名或密码错误"),
    USER_NOT_FOUND(10009, "用户不存在"),

    SYSTEM_ERROR(5000, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}