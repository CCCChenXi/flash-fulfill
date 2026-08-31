package com.flash.fulfill.product.price;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.constant.RedisKeys;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.product.entity.Sku;
import com.flash.fulfill.product.mapper.SkuMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * 秒杀价格查询服务(Redis 优先,加锁重建防击穿)。
 * <p>
 * 读取 {@code seckill:price:{skuId}}:命中直接返回;未命中加分布式锁(seckill:price:lock:{skuId})
 * 防缓存击穿,持锁线程查 DB 并回填缓存;未抢到锁的线程短暂等待后重读,仍缺失则直查 DB 不回填。
 * <p>
 * TODO 生产:锁释放建议改 Lua compare-and-delete,当前为直接删除(价格重建毫秒级,风险可接受)。
 */
@Slf4j
@Service
public class SkuPriceService {

    private static final Duration PRICE_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final long LOCK_WAIT_MILLIS = 50L;

    private final StringRedisTemplate redisTemplate;
    private final SkuMapper skuMapper;

    public SkuPriceService(StringRedisTemplate redisTemplate, SkuMapper skuMapper) {
        this.redisTemplate = redisTemplate;
        this.skuMapper = skuMapper;
    }

    public BigDecimal getPrice(Long skuId) {
        BigDecimal cached = readCache(skuId);
        if (cached != null) {
            return cached;
        }
        return rebuild(skuId);
    }

    private BigDecimal readCache(Long skuId) {
        String key = RedisKeys.SECKILL_PRICE_PREFIX + skuId;
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            BigDecimal price = new BigDecimal(value);
            if (price.signum() <= 0) {
                log.warn("秒杀价格缓存非法 skuId={} value={}", skuId, value);
                return null;
            }
            return price;
        } catch (NumberFormatException e) {
            log.warn("秒杀价格缓存解析失败 skuId={} key={}", skuId, key);
            return null;
        } catch (RuntimeException e) {
            log.warn("读秒杀价格缓存异常 skuId={} err={}", skuId, e.getMessage());
            return null;
        }
    }

    private BigDecimal rebuild(Long skuId) {
        String lockKey = RedisKeys.SECKILL_PRICE_LOCK_PREFIX + skuId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, UUID.randomUUID().toString(), LOCK_TTL);
        if (Boolean.TRUE.equals(locked)) {
            try {
                BigDecimal again = readCache(skuId);
                if (again != null) {
                    return again;
                }
                BigDecimal price = loadFromDb(skuId);
                if (price != null) {
                    redisTemplate.opsForValue().set(
                            RedisKeys.SECKILL_PRICE_PREFIX + skuId, price.toPlainString(), PRICE_TTL);
                    log.info("秒杀价格缓存重建 skuId={} price={}", skuId, price);
                }
                return price;
            } finally {
                releaseLock(lockKey);
            }
        }
        sleep(LOCK_WAIT_MILLIS);
        BigDecimal after = readCache(skuId);
        if (after != null) {
            return after;
        }
        return loadFromDb(skuId);
    }

    private BigDecimal loadFromDb(Long skuId) {
        Sku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND, "SKU 不存在:" + skuId);
        }
        return sku.getPrice();
    }

    private void releaseLock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (RuntimeException e) {
            log.warn("释放价格重建锁失败 lockKey={} err={}", lockKey, e.getMessage());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}