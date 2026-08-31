package com.flash.fulfill.product.price;

import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.product.entity.Sku;
import com.flash.fulfill.product.mapper.SkuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkuPriceServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private SkuMapper skuMapper;
    private SkuPriceService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        skuMapper = mock(SkuMapper.class);
        service = new SkuPriceService(redisTemplate, skuMapper);
    }

    private Sku buildSku(BigDecimal price) {
        Sku sku = new Sku();
        sku.setId(1001L);
        sku.setPrice(price);
        return sku;
    }

    @Test
    void returnsCachedPriceWithoutLockOrDb() {
        when(valueOps.get("seckill:price:1001")).thenReturn("199.00");

        BigDecimal price = service.getPrice(1001L);

        assertEquals(new BigDecimal("199.00"), price);
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(skuMapper, never()).selectById(any());
    }

    @Test
    void rebuildsFromDbAndBackfillsCacheWhenCacheMiss() {
        when(valueOps.get("seckill:price:1001")).thenReturn(null);
        when(valueOps.setIfAbsent(eq("seckill:price:lock:1001"), anyString(), any(Duration.class))).thenReturn(true);
        when(skuMapper.selectById(1001L)).thenReturn(buildSku(new BigDecimal("99.00")));

        BigDecimal price = service.getPrice(1001L);

        assertEquals(new BigDecimal("99.00"), price);
        verify(valueOps).set(eq("seckill:price:1001"), eq("99.00"), any(Duration.class));
        verify(redisTemplate).delete("seckill:price:lock:1001");
    }

    @Test
    void returnsCachedAfterWaitingWhenLockContended() {
        when(valueOps.get("seckill:price:1001")).thenReturn(null, "199.00");
        when(valueOps.setIfAbsent(eq("seckill:price:lock:1001"), anyString(), any(Duration.class))).thenReturn(false);

        BigDecimal price = service.getPrice(1001L);

        assertEquals(new BigDecimal("199.00"), price);
        verify(skuMapper, never()).selectById(any());
    }

    @Test
    void throwsWhenSkuMissingInDbAndReleasesLock() {
        when(valueOps.get("seckill:price:1001")).thenReturn(null);
        when(valueOps.setIfAbsent(eq("seckill:price:lock:1001"), anyString(), any(Duration.class))).thenReturn(true);
        when(skuMapper.selectById(1001L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.getPrice(1001L));

        verify(redisTemplate).delete("seckill:price:lock:1001");
    }
}