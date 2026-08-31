package com.flash.fulfill.product.constant;

/**
 * 商品服务私有缓存 key 常量。
 */
public final class ProductCacheKeys {

    /** SKU 出售视图缓存:product:sku:{skuId},仅商品服务读写(下游经 Feign 消费,不直接读 Redis) */
    public static final String SKU_VIEW_PREFIX = "product:sku:";

    private ProductCacheKeys() {
    }
}