package com.flash.fulfill.common.constant;

/**
 * 秒杀入口业务状态码。
 * <p>
 * 0 成功;1 参数非法;2 商品下架;3 库存不足;4 商品或库存不存在;5 处理中/重复;6 已达限购上限;7 商品价格未就绪。
 */
public final class SeckillResultCode {

    public static final int SUCCESS = 0;
    public static final int INVALID_PARAM = 1;
    public static final int OFF_SHELF = 2;
    public static final int STOCK_NOT_ENOUGH = 3;
    public static final int NOT_EXIST = 4;
    public static final int PROCESSING = 5;
    public static final int LIMIT = 6;
    public static final int PRICE_UNAVAILABLE = 7;

    private SeckillResultCode() {
    }
}
