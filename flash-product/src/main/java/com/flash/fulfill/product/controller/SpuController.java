package com.flash.fulfill.product.controller;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.product.dto.SpuCreateCommand;
import com.flash.fulfill.product.dto.SpuUpdateCommand;
import com.flash.fulfill.product.dto.SpuView;
import com.flash.fulfill.product.service.SpuService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SPU 商品接口。
 */
@RestController
@RequestMapping("/api/product/spu")
public class SpuController {

    private final SpuService spuService;

    public SpuController(SpuService spuService) {
        this.spuService = spuService;
    }

    /** 新建 SPU */
    @PostMapping
    public Result<SpuView> create(@Valid @RequestBody SpuCreateCommand cmd) {
        return Result.ok(spuService.create(cmd));
    }

    /** 更新 SPU */
    @PutMapping("/{id}")
    public Result<SpuView> update(@PathVariable("id") Long id, @RequestBody SpuUpdateCommand cmd) {
        return Result.ok(spuService.update(id, cmd));
    }

    /** 上架 */
    @PutMapping("/{id}/on-shelf")
    public Result<SpuView> onShelf(@PathVariable("id") Long id) {
        return Result.ok(spuService.onShelf(id));
    }

    /** 下架 */
    @PutMapping("/{id}/off-shelf")
    public Result<SpuView> offShelf(@PathVariable("id") Long id) {
        return Result.ok(spuService.offShelf(id));
    }

    /** 查询 SPU */
    @GetMapping("/{id}")
    public Result<SpuView> get(@PathVariable("id") Long id) {
        return Result.ok(spuService.get(id));
    }
}
