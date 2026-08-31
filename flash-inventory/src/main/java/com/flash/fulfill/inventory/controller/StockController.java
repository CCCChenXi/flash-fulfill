package com.flash.fulfill.inventory.controller;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.ApiPaths;
import com.flash.fulfill.inventory.entity.Stock;
import com.flash.fulfill.inventory.service.StockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存接口。
 * <p>
 * 扣减库存已改为 MQ 命令驱动(INVENTORY_DEDUCT),不再提供 Feign 同步接口。
 */
@RestController
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping(ApiPaths.INVENTORY_STOCKS)
    public Result<Stock> query(@PathVariable("skuId") Long skuId) {
        return Result.ok(stockService.query(skuId));
    }
}