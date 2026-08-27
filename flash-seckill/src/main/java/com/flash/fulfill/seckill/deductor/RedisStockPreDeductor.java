package com.flash.fulfill.seckill.deductor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的简化预扣实现。
 * <p>
 * TODO 生产实现建议使用 Lua 脚本:KEYS[1] 检查存在并初始化、DECR 校验是否 < 0、失败回滚
 * <p>
 * 注意:演示链路中 redis 预扣与库存服务 DB 扣减是两套库存,骨架阶段仅用于验证"预扣→MQ→建单→扣减"链路,
 * 生产需通过"事务消息 + 对账/补偿"保证最终一致(零超卖、不漏单)。
 */
@Slf4j
@Component
public class RedisStockPreDeductor implements StockPreDeductor {

    private static final String KEY_PREFIX = "seckill:stock:";

    private final StringRedisTemplate redisTemplate;

    @Value("${seckill.stock.default:100}")
    private int defaultStock;

    public RedisStockPreDeductor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryPreDeduct(Long skuId, int quantity) {
        if (skuId == null || quantity <= 0) {
            return false;
        }
        String key = key(skuId);
        String init = redisTemplate.opsForValue().get(key);
        if (init == null) {
            Boolean set = redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(defaultStock));
            if (Boolean.TRUE.equals(set)) {
                redisTemplate.expire(key, Duration.ofHours(24));
            }
        }
        Long left = redisTemplate.opsForValue().decrement(key, quantity);
        if (left == null || left < 0) {
            redisTemplate.opsForValue().increment(key, quantity);
            return false;
        }
        return true;
    }

    @Override
    public void rollback(Long skuId, int quantity) {
        redisTemplate.opsForValue().increment(key(skuId), quantity);
    }

    private String key(Long skuId) {
        return KEY_PREFIX + skuId;
    }
}