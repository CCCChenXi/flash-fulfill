package com.flash.fulfill.product.controller;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.dto.SkuSellView;
import com.flash.fulfill.product.dto.SkuCreateCommand;
import com.flash.fulfill.product.dto.SkuStatusCommand;
import com.flash.fulfill.product.dto.SkuUpdateCommand;
import com.flash.fulfill.product.dto.SkuView;
import com.flash.fulfill.product.service.SkuService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SKU 商品接口。
 */
@RestController
@RequestMapping("/api/product/sku")
public class SkuController {

    private final SkuService skuService;

    public SkuController(SkuService skuService) {
        this.skuService = skuService;
    }

    /** 新建 SKU */
    @PostMapping
    public Result<SkuView> create(@Valid @RequestBody SkuCreateCommand cmd) {
        return Result.ok(skuService.create(cmd));
    }

    /** 更新 SKU */
    @PutMapping("/{id}")
    public Result<SkuView> update(@PathVariable("id") Long id, @RequestBody SkuUpdateCommand cmd) {
        return Result.ok(skuService.update(id, cmd));
    }

    /** 设置 SKU 状态上 / 下架 */
    @PutMapping("/{id}/status")
    public Result<SkuView> setStatus(@PathVariable("id") Long id, @RequestBody SkuStatusCommand cmd) {
        return Result.ok(skuService.setStatus(id, cmd.getStatus()));
    }

    /** 查询 SKU 出售视图(下游 order / seckill 跨服务消费) */
    @GetMapping("/{skuId}")
    public Result<SkuSellView> get(@PathVariable("skuId") Long skuId) {
        return Result.ok(skuService.getSellView(skuId));
    }
}
