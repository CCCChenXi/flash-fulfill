package com.flash.fulfill.inventory.controller;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.dto.DeductStockCommand;
import com.flash.fulfill.common.dto.DeductStockResult;
import com.flash.fulfill.inventory.entity.Stock;
import com.flash.fulfill.inventory.service.StockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存接口。
 */
@RestController
@RequestMapping("/api/inventory")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /**
     * 内网扣减库存(订单服务通过 Feign 调用)。
     */
    @PostMapping("/internal/deduct")
    public Result<DeductStockResult> deduct(@RequestBody DeductStockCommand command) {
        return Result.ok(stockService.deduct(command));
    }

    @GetMapping("/stocks/{skuId}")
    public Result<Stock> query(@PathVariable("skuId") Long skuId) {
        return Result.ok(stockService.query(skuId));
    }
}