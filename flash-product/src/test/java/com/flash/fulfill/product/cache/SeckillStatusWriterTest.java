package com.flash.fulfill.product.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillStatusWriterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private SeckillStatusWriter writer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        writer = new SeckillStatusWriter(redisTemplate);
    }

    @Test
    void skuPriceWritesPlainStringWithTtl() {
        writer.skuPrice(1001L, new BigDecimal("99.00"));

        verify(valueOps).set(eq("seckill:price:1001"), eq("99.00"), any(Duration.class));
    }

    @Test
    void skuPriceFailureDoesNotThrow() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        assertDoesNotThrow(() -> writer.skuPrice(1001L, new BigDecimal("99.00")));
    }
}