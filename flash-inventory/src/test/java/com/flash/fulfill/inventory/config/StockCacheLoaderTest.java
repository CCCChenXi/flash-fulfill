package com.flash.fulfill.inventory.config;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.flash.fulfill.inventory.entity.Stock;
import com.flash.fulfill.inventory.mapper.StockMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockCacheLoaderTest {

    private StockMapper stockMapper;
    private StringRedisTemplate redisTemplate;
    private StockCacheLoader loader;

    @BeforeEach
    void setUp() {
        stockMapper = mock(StockMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        loader = new StockCacheLoader(stockMapper, redisTemplate);
    }

    private Stock stock(Long skuId, int available) {
        Stock s = new Stock();
        s.setSkuId(skuId);
        s.setAvailable(available);
        return s;
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadsAvailableStocksToRedisViaPipeline() {
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(
                List.of(stock(1001L, 100), stock(1002L, 50)));

        loader.run(null);

        verify(redisTemplate).executePipelined(any(RedisCallback.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void skipsWhenNoAvailableStocks() {
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        loader.run(null);

        verify(redisTemplate, never()).executePipelined(any(RedisCallback.class));
    }
}