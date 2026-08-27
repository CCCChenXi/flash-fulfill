package com.flash.fulfill.product.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.dto.SkuSellView;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.product.entity.Sku;
import com.flash.fulfill.product.entity.Spu;
import com.flash.fulfill.product.mapper.SkuMapper;
import com.flash.fulfill.product.mapper.SpuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SkuCacheServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ObjectMapper objectMapper;
    private SkuMapper skuMapper;
    private SpuMapper spuMapper;
    private SkuCacheService service;

    private static final String KEY = "product:sku:200";

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        objectMapper = mock(ObjectMapper.class);
        skuMapper = mock(SkuMapper.class);
        spuMapper = mock(SpuMapper.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new SkuCacheService(redisTemplate, objectMapper, 600L, skuMapper, spuMapper);
    }

    private Sku buildSku() {
        Sku sku = new Sku();
        sku.setId(200L);
        sku.setSpuId(100L);
        sku.setSkuCode("SKU001");
        sku.setName("华为 Mate 60 Pro");
        sku.setPrice(new BigDecimal("5999.00"));
        sku.setStatus(1);
        return sku;
    }

    private Spu buildSpu() {
        Spu spu = new Spu();
        spu.setId(100L);
        spu.setStatus(0);
        return spu;
    }

    private SkuSellView buildView() {
        SkuSellView v = new SkuSellView();
        v.setSkuId(200L);
        v.setSpuId(100L);
        v.setSkuName("华为 Mate 60 Pro");
        v.setPrice(new BigDecimal("5999.00"));
        v.setSkuStatus(1);
        v.setSpuStatus(0);
        return v;
    }

    @Test
    void getSellViewHitReturnsCached() throws Exception {
        SkuSellView cached = buildView();
        when(valueOps.get(KEY)).thenReturn("{\"json\":true}");
        when(objectMapper.readValue("{\"json\":true}", SkuSellView.class)).thenReturn(cached);

        SkuSellView result = service.getSellView(200L);

        assertEquals(cached, result);
        verifyNoInteractions(skuMapper, spuMapper);
    }

    @Test
    void getSellViewMissLoadsAndWrites() throws Exception {
        when(valueOps.get(KEY)).thenReturn(null);
        when(skuMapper.selectById4View(200L)).thenReturn(buildSku());
        when(spuMapper.selectById4View(100L)).thenReturn(buildSpu());
        when(objectMapper.writeValueAsString(any(SkuSellView.class))).thenReturn("{\"json\":true}");

        SkuSellView result = service.getSellView(200L);

        assertEquals(200L, result.getSkuId());
        assertEquals(100L, result.getSpuId());
        assertEquals("华为 Mate 60 Pro", result.getSkuName());
        assertEquals(new BigDecimal("5999.00"), result.getPrice());
        assertEquals(1, result.getSkuStatus());
        assertEquals(0, result.getSpuStatus());
        verify(valueOps).set(eq(KEY), eq("{\"json\":true}"), any(Duration.class));
    }

    @Test
    void getSellViewRedisGetFailsFallsBackToDb() {
        when(valueOps.get(KEY)).thenThrow(new RuntimeException("redis down"));
        when(skuMapper.selectById4View(200L)).thenReturn(buildSku());
        when(spuMapper.selectById4View(100L)).thenReturn(buildSpu());

        SkuSellView result = service.getSellView(200L);

        assertEquals(200L, result.getSkuId());
        assertEquals(0, result.getSpuStatus());
    }

    @Test
    void evictSkuDeletesKey() {
        service.evictSku(200L);

        verify(redisTemplate).delete(KEY);
    }

    @Test
    void evictSkuRedisDeleteFailsNoThrow() {
        doThrow(new RuntimeException("redis down")).when(redisTemplate).delete(KEY);

        assertDoesNotThrow(() -> service.evictSku(200L));
    }

    @Test
    void evictSpuDeletesAllChildSkuKeys() {
        when(skuMapper.selectIdsBySpuId(100L)).thenReturn(List.of(200L, 201L));

        service.evictSpu(100L);

        verify(skuMapper).selectIdsBySpuId(100L);
        verify(redisTemplate).delete("product:sku:200");
        verify(redisTemplate).delete("product:sku:201");
    }

    @Test
    void getSellViewSkuMissingThrows() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(skuMapper.selectById4View(200L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.getSellView(200L));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND.getCode(), ex.getCode());
    }
}
