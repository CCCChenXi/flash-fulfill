package com.flash.fulfill.product.cache;

import com.flash.fulfill.common.constant.ProductStatus;
import com.flash.fulfill.common.constant.RedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * 秒杀轻量缓存 key 写入器。
 * <p>
 * 供 flash-product 在 SPU/SKU 新建 / 更新 / 上下架后同步写入:
 * <ul>
 *   <li>{@code seckill:spu:status:{spuId}} / {@code seckill:sku:status:{skuId}},值 "1"(上架)或 "0"(下架)</li>
 *   <li>{@code seckill:price:{skuId}},值为价格字符串,订单侧建单计价读取</li>
 * </ul>
 * 写入失败仅告警不抛出,避免影响商品主流程(秒杀侧对 key 缺失按"不存在"兜底)。
 */
@Slf4j
@Component
public class SeckillStatusWriter {

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public SeckillStatusWriter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void spuStatus(Long spuId, boolean on) {
        write(RedisKeys.SECKILL_SPU_STATUS_PREFIX + spuId, on);
    }

    public void skuStatus(Long skuId, boolean on) {
        write(RedisKeys.SECKILL_SKU_STATUS_PREFIX + skuId, on);
    }

    /** 写入秒杀价格 key,订单侧建单计价读取。 */
    public void skuPrice(Long skuId, BigDecimal price) {
        if (price == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    RedisKeys.SECKILL_PRICE_PREFIX + skuId, price.toPlainString(), TTL);
        } catch (Exception e) {
            log.warn("写入秒杀价格 key 失败 skuId={} err={}", skuId, e.getMessage());
        }
    }

    private void write(String key, boolean on) {
        try {
            redisTemplate.opsForValue().set(key, on ? ProductStatus.ON_SHELF_VALUE : ProductStatus.OFF_SHELF_VALUE, TTL);
        } catch (Exception e) {
            log.warn("写入秒杀状态 key 失败 key={} err={}", key, e.getMessage());
        }
    }
}
