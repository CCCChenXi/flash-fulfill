package com.flash.fulfill.common.constant;

/**
 * 跨服务共享的 Redis Key 前缀与分隔约定。
 * <p>
 * 会话键(flash-user 写入 / flash-gateway 校验)与秒杀键(flash-product 写入状态 / flash-seckill 读取)
 * 被多个服务引用,故统一收敛到 common,避免各服务各自硬编码产生漂移。
 */
public final class RedisKeys {

    /** 用户会话:user:session:{tokenHash} */
    public static final String USER_SESSION_PREFIX = "user:session:";

    /** 秒杀幂等状态:seckill:req:{requestId} */
    public static final String SECKILL_REQ_PREFIX = "seckill:req:";

    /** 秒杀库存:seckill:stock:{skuId} */
    public static final String SECKILL_STOCK_PREFIX = "seckill:stock:";

    /** 秒杀 SKU 状态(供 flash-product 写、flash-seckill 读):seckill:sku:status:{skuId} */
    public static final String SECKILL_SKU_STATUS_PREFIX = "seckill:sku:status:";

    /** 秒杀价格(供 flash-product 写、flash-order 建单计价读):seckill:price:{skuId} */
    public static final String SECKILL_PRICE_PREFIX = "seckill:price:";

    /** 秒杀价格重建锁(防缓存击穿,供 flash-product 用):seckill:price:lock:{skuId} */
    public static final String SECKILL_PRICE_LOCK_PREFIX = "seckill:price:lock:";

    /** 秒杀 SPU 状态(供 flash-product 写、flash-seckill 读):seckill:spu:status:{spuId} */
    public static final String SECKILL_SPU_STATUS_PREFIX = "seckill:spu:status:";

    /** 秒杀限购计数:seckill:buy:{userId}:{activityId}:{skuId} */
    public static final String SECKILL_BUY_PREFIX = "seckill:buy:";

    /** 秒杀限购计数各段的分隔符 */
    public static final String BUY_KEY_SEPARATOR = ":";

    /** 秒杀建单事件 Stream(flash-seckill Lua XADD,flash-relay 消费转发 RocketMQ) */
    public static final String SECKILL_EVENT_STREAM = "seckill:event:order";

    /** 秒杀建单事件 Stream 消费组 */
    public static final String SECKILL_EVENT_GROUP = "flash-seckill-order-relay";

    private RedisKeys() {
    }
}
