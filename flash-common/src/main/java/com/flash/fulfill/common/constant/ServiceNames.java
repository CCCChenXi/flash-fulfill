package com.flash.fulfill.common.constant;

/**
 * 微服务注册名(Nacos 注册名 / Feign 客户端 name / 网关路由 uri 引用)。
 * <p>
 * 网关路由、Feign 客户端、跨服务调用统一引用,避免各模块硬编码服务名漂移。
 */
public final class ServiceNames {

    public static final String GATEWAY = "flash-gateway";
    public static final String SECKILL = "flash-seckill";
    public static final String ORDER = "flash-order";
    public static final String INVENTORY = "flash-inventory";
    public static final String FULFILLMENT = "flash-fulfillment";
    public static final String USER = "flash-user";
    public static final String PRODUCT = "flash-product";
    public static final String PAYMENT = "flash-payment";

    private ServiceNames() {
    }
}