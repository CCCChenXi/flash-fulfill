package com.flash.fulfill.inventory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 库存中心入口。
 * <p>
 * TODO 生产增强:ShardingSphere 按 sku 基因分库分表 + 库存扣减走 Lua/Redis 预占 + 数据库最终对账,
 * 骨架版为单库乐观锁条件更新。
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.flash.fulfill.inventory.mapper")
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}