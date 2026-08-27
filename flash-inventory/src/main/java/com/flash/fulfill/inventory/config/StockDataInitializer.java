package com.flash.fulfill.inventory.config;

import com.flash.fulfill.inventory.entity.Stock;
import com.flash.fulfill.inventory.mapper.StockMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 演示数据初始化:为内置 SKU 预热库存。
 * <p>
 * TODO 生产:库存数据由商品/采购中心维护,通过 MQ 广播或管理端写入,此处仅演示。
 */
@Slf4j
@Component
public class StockDataInitializer implements ApplicationRunner {

    private final StockMapper stockMapper;

    @Value("${inventory.seed.enabled:true}")
    private boolean seedEnabled;

    public StockDataInitializer(StockMapper stockMapper) {
        this.stockMapper = stockMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        seedIfAbsent(1001L, "限量款智能手机", 100);
        seedIfAbsent(1002L, "联名限量跑鞋", 50);
    }

    private void seedIfAbsent(Long skuId, String name, int available) {
        if (stockMapper.selectBySkuId(skuId) == null) {
            Stock stock = new Stock();
            stock.setSkuId(skuId);
            stock.setSkuName(name);
            stock.setAvailable(available);
            stock.setLocked(0);
            stock.setVersion(0);
            stockMapper.insert(stock);
            log.info("已初始化库存 skuId={} name={} available={}", skuId, name, available);
        }
    }
}
