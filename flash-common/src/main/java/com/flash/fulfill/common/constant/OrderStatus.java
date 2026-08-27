package com.flash.fulfill.common.constant;

/**
 * 订单状态机(骨架版本)。
 * INITIAL -> CREATED -> DISPATCHED
 * INITIAL -> FAILED
 * CREATED -> CLOSED(超时关单, TODO 延迟消息实现)
 */
public final class OrderStatus {

    private OrderStatus() {
    }

    /** 创建中(已接收 MQ 命令) */
    public static final String INITIAL = "INITIAL";
    /** 已创建(支付成功占位) */
    public static final String CREATED = "CREATED";
    /** 创建失败(如库存扣减失败) */
    public static final String FAILED = "FAILED";
    /** 已发货(履约完成) */
    public static final String DISPATCHED = "DISPATCHED";
    /** 已关闭(超时未支付) */
    public static final String CLOSED = "CLOSED";
}