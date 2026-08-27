package com.flash.fulfill.product.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.dto.SkuSellView;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.product.entity.Sku;
import com.flash.fulfill.product.entity.Spu;
import com.flash.fulfill.product.mapper.SkuMapper;
import com.flash.fulfill.product.mapper.SpuMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * SKU 出售视图缓存:key = {@code product:sku:{skuId}},值为 SkuSellView JSON,TTL 取 product.cache.secs。
 * <p>
 * 命中直接返回;未命中则回源 DB(sku + 父 spu)构建后回填再返回。
 */
@Slf4j
@Service
public class SkuCacheService {

    private static final String KEY_PREFIX = "product:sku:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;
    private final SkuMapper skuMapper;
    private final SpuMapper spuMapper;

    public SkuCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${product.cache.secs:600}") long ttlSeconds,
            SkuMapper skuMapper,
            SpuMapper spuMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttlSeconds = ttlSeconds;
        this.skuMapper = skuMapper;
        this.spuMapper = spuMapper;
    }

    /** 出售视图:命中缓存直接返回,未命中回源 DB(含父 SPU 状态)并回填。 */
    public SkuSellView getSellView(Long skuId) {
        String key = keyOf(skuId);
        String json = redisTemplate.opsForValue().get(key);
        if (json != null) {
            try {
                return objectMapper.readValue(json, SkuSellView.class);
            } catch (JsonProcessingException e) {
                log.warn("SKU 出售视图缓存解析失败,回源重建 key={} err={}", key, e.getMessage());
            }
        }
        SkuSellView view = loadFromDb(skuId);
        writeCache(key, view);
        return view;
    }

    /** 删除单个 SKU 缓存。 */
    public void evictSku(Long skuId) {
        redisTemplate.delete(keyOf(skuId));
    }

    /** 删除指定 SPU 下所有 SKU 出售视图缓存(SPU 状态/名称变更影响子 SKU 视图)。 */
    public void evictSpu(Long spuId) {
        List<Long> skuIds = skuMapper.selectIdsBySpuId(spuId);
        if (skuIds != null) {
            skuIds.forEach(this::evictSku);
        }
    }

    private SkuSellView loadFromDb(Long skuId) {
        Sku sku = skuMapper.selectById4View(skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        Spu spu = spuMapper.selectById4View(sku.getSpuId());
        SkuSellView v = new SkuSellView();
        v.setSkuId(sku.getId());
        v.setSpuId(sku.getSpuId());
        v.setSkuName(sku.getName());
        v.setPrice(sku.getPrice());
        v.setSkuStatus(sku.getStatus());
        v.setSpuStatus(spu == null ? null : spu.getStatus());
        return v;
    }

    private void writeCache(String key, SkuSellView view) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(view), Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException e) {
            log.warn("SKU 出售视图写缓存失败 key={} err={}", key, e.getMessage());
        }
    }

    private String keyOf(Long skuId) {
        return KEY_PREFIX + skuId;
    }
}
