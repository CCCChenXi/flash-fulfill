package com.flash.fulfill.common.constant;

/**
 * 跨服务 API 路径常量。
 * <p>
 * 网关(路由/鉴权白名单/秒杀参数校验)与各服务 Feign 客户端 / Controller 共用,
 * 避免路径字符串在各处硬编码漂移。
 */
public final class ApiPaths {

    /** 用户服务 */
    public static final String USER_BASE = "/api/user";
    public static final String USER_LOGIN = "/api/user/login";
    public static final String USER_REGISTER = "/api/user/register";
    public static final String USER_ME = "/api/user/me";

    /** 秒杀服务 */
    public static final String SECKILL_BASE = "/api/seckill";
    public static final String SECKILL_FLASH_ORDERS = "/api/seckill/flash-orders";

    /** 订单服务 */
    public static final String ORDER_BASE = "/api/order";
    public static final String ORDER_INTERNAL_BASE = "/api/order/internal";
    public static final String ORDER_FLASH_ORDERS = "/api/order/flash-orders";
    public static final String ORDER_ORDERS = "/api/order/orders";
    public static final String ORDER_CONFIRM = "/api/order/orders/{orderNo}/confirm";
    public static final String ORDER_CONFIRM_RECEIPT = "/api/order/orders/{orderNo}/confirm-receipt";
    public static final String ORDER_INTERNAL_DISPATCH = "/api/order/internal/orders/{orderNo}/dispatched";
    public static final String ORDER_INTERNAL_PAID = "/api/order/internal/orders/{orderNo}/paid";
    public static final String ORDER_INTERNAL_COMPLETE = "/api/order/internal/orders/{orderNo}/completed";
    public static final String ORDER_INTERNAL_CANCEL = "/api/order/internal/orders/{orderNo}/cancel";

    /** 库存服务 */
    public static final String INVENTORY_BASE = "/api/inventory";
    public static final String INVENTORY_DEDUCT = "/api/inventory/internal/deduct";
    public static final String INVENTORY_STOCKS = "/api/inventory/stocks/{skuId}";

    /** 商品服务 */
    public static final String PRODUCT_BASE = "/api/product";
    public static final String PRODUCT_SKU_BASE = "/api/product/sku";
    public static final String PRODUCT_SPU_BASE = "/api/product/spu";
    public static final String PRODUCT_SKU_VIEW = "/api/product/sku/{skuId}";
    public static final String PRODUCT_SKU_PRICE = "/api/product/sku/{skuId}/price";

    /** 履约服务 */
    public static final String FULFILLMENT_BASE = "/api/fulfillment";
    public static final String FULFILLMENT_DISPATCH = "/api/fulfillment/dispatch/{orderNo}";

    /** 支付服务 */
    public static final String PAYMENT_BASE = "/api/payment";
    public static final String PAYMENT_PAY = "/api/payment/pay";
    public static final String PAYMENT_CALLBACK = "/api/payment/callback";
    public static final String PAYMENT_QUERY = "/api/payment/pay/{orderNo}";

    private ApiPaths() {
    }
}