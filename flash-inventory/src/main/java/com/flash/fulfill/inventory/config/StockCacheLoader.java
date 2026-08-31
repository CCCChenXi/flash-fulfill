package com.flash.fulfill.inventory.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flash.fulfill.common.constant.RedisKeys;
import com.flash.fulfill.inventory.entity.Stock;
import com.flash.fulfill.inventory.mapper.StockMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 库存 Redis 加载器:服务启动时把 DB 中可用库存(available &gt; 0)批量加载到 Redis 秒杀库存 key。
 * <p>
 * 秒杀侧 Lua 脚本据此扣减,不再设置默认库存;Redis 库存 key 缺失即视为商品不存在。
 * key = {@code seckill:stock:{skuId}},不设 TTL,覆盖写入(以 DB 为准)。
 * <p>
 * TODO 生产:DB 可用库存变更后需实时同步到 Redis(本轮仅启动加载,不做双向对账)。
 */
@Slf4j
@Component
public class StockCacheLoader implements ApplicationRunner {

    private final StockMapper stockMapper;
    private final StringRedisTemplate redisTemplate;

    public StockCacheLoader(StockMapper stockMapper, StringRedisTemplate redisTemplate) {
        this.stockMapper = stockMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Stock> stocks = stockMapper.selectList(
                new QueryWrapper<Stock>().gt("available", 0));
        if (stocks == null || stocks.isEmpty()) {
            log.info("无可加载的库存数据,跳过 Redis 库存预热");
            return;
        }
        redisTemplate.executePipelined((RedisConnection connection) -> {
            for (Stock stock : stocks) {
                String key = RedisKeys.SECKILL_STOCK_PREFIX + stock.getSkuId();
                connection.stringCommands().set(
                        key.getBytes(StandardCharsets.UTF_8),
                        String.valueOf(stock.getAvailable()).getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        log.info("已将 {} 条可用库存加载到 Redis 秒杀库存", stocks.size());
    }
}