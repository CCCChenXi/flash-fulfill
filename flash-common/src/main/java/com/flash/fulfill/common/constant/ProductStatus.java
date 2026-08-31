package com.flash.fulfill.common.constant;

/**
 * 商品状态(上架/下架)通用常量,供 flash-product(状态 key 写入)、flash-seckill(Lua 状态判断)、
 * flash-order(计价校验)共用,避免各服务硬编码 0/1 与 "1"/"0"。
 */
public final class ProductStatus {

    /** 上架(DB TINYINT) */
    public static final int ON_SHELF = 1;

    /** 下架(DB TINYINT) */
    public static final int OFF_SHELF = 0;

    /** Redis 状态 key 的值:上架 */
    public static final String ON_SHELF_VALUE = "1";

    /** Redis 状态 key 的值:下架 */
    public static final String OFF_SHELF_VALUE = "0";

    private ProductStatus() {
    }
}
