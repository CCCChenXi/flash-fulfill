package com.flash.fulfill.product.controller;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.ApiPaths;
import com.flash.fulfill.common.dto.SkuSellView;
import com.flash.fulfill.product.dto.SkuCreateCommand;
import com.flash.fulfill.product.dto.SkuStatusCommand;
import com.flash.fulfill.product.dto.SkuUpdateCommand;
import com.flash.fulfill.product.dto.SkuView;
import com.flash.fulfill.product.price.SkuPriceService;
import com.flash.fulfill.product.service.SkuService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * SKU 商品接口。
 */
@RestController
public class SkuController {

    private final SkuService skuService;
    private final SkuPriceService skuPriceService;

    public SkuController(SkuService skuService, SkuPriceService skuPriceService) {
        this.skuService = skuService;
        this.skuPriceService = skuPriceService;
    }

    /** 新建 SKU */
    @PostMapping(ApiPaths.PRODUCT_SKU_BASE)
    public Result<SkuView> create(@Valid @RequestBody SkuCreateCommand cmd) {
        return Result.ok(skuService.create(cmd));
    }

    /** 更新 SKU */
    @PutMapping(ApiPaths.PRODUCT_SKU_BASE + "/{id}")
    public Result<SkuView> update(@PathVariable("id") Long id, @RequestBody SkuUpdateCommand cmd) {
        return Result.ok(skuService.update(id, cmd));
    }

    /** 设置 SKU 状态上 / 下架 */
    @PutMapping(ApiPaths.PRODUCT_SKU_BASE + "/{id}/status")
    public Result<SkuView> setStatus(@PathVariable("id") Long id, @RequestBody SkuStatusCommand cmd) {
        return Result.ok(skuService.setStatus(id, cmd.getStatus()));
    }

    /** 查询 SKU 出售视图(下游 order / seckill 跨服务消费) */
    @GetMapping(ApiPaths.PRODUCT_SKU_VIEW)
    public Result<SkuSellView> get(@PathVariable("skuId") Long skuId) {
        return Result.ok(skuService.getSellView(skuId));
    }

    /** 查询 SKU 当前单价(Redis 优先,加锁重建;订单侧计价降级兜底) */
    @GetMapping(ApiPaths.PRODUCT_SKU_PRICE)
    public Result<BigDecimal> getPrice(@PathVariable("skuId") Long skuId) {
        return Result.ok(skuPriceService.getPrice(skuId));
    }
}
