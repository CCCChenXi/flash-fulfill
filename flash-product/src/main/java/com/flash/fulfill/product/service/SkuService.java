package com.flash.fulfill.product.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.dto.SkuSellView;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.product.cache.SkuCacheService;
import com.flash.fulfill.product.dto.SkuCreateCommand;
import com.flash.fulfill.product.dto.SkuUpdateCommand;
import com.flash.fulfill.product.dto.SkuView;
import com.flash.fulfill.product.entity.Sku;
import com.flash.fulfill.product.mapper.SkuMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SKU 服务:新建 / 更新 / 上下架 / 出售视图。
 */
@Slf4j
@Service
public class SkuService {

    public static final int STATUS_ON_SHELF = 1;
    public static final int STATUS_OFF_SHELF = 0;

    private final SkuMapper skuMapper;
    private final SkuCacheService cacheService;

    public SkuService(SkuMapper skuMapper, SkuCacheService cacheService) {
        this.skuMapper = skuMapper;
        this.cacheService = cacheService;
    }

    /** 新建 SKU,默认上架;sku_code 唯一。 */
    @Transactional
    public SkuView create(SkuCreateCommand cmd) {
        if (skuMapper.selectBySkuCode(cmd.getSkuCode()) != null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "SKU编码已存在");
        }
        Sku sku = new Sku();
        sku.setSpuId(cmd.getSpuId());
        sku.setSkuCode(cmd.getSkuCode());
        sku.setName(cmd.getName());
        sku.setPrice(cmd.getPrice());
        sku.setImage(cmd.getImage());
        sku.setSpecs(cmd.getSpecs());
        sku.setStatus(STATUS_ON_SHELF);
        skuMapper.insert(sku);
        cacheService.evictSku(sku.getId());
        log.info("新建 SKU 成功 skuId={}", sku.getId());
        return toView(sku);
    }

    /** 部分更新:传入的字段为 null 时不修改。 */
    @Transactional
    public SkuView update(Long id, SkuUpdateCommand cmd) {
        Sku sku = requireSku(id);
        if (cmd.getName() != null) {
            sku.setName(cmd.getName());
        }
        if (cmd.getPrice() != null) {
            sku.setPrice(cmd.getPrice());
        }
        if (cmd.getImage() != null) {
            sku.setImage(cmd.getImage());
        }
        if (cmd.getSpecs() != null) {
            sku.setSpecs(cmd.getSpecs());
        }
        if (cmd.getStatus() != null) {
            sku.setStatus(cmd.getStatus());
        }
        skuMapper.updateById(sku);
        cacheService.evictSku(id);
        log.info("更新 SKU 成功 skuId={}", id);
        return toView(sku);
    }

    @Transactional
    public SkuView setStatus(Long id, int status) {
        if (status != STATUS_ON_SHELF && status != STATUS_OFF_SHELF) {
            throw new BizException(ErrorCode.INVALID_PARAM, "status 只能为 0 或 1");
        }
        Sku sku = requireSku(id);
        sku.setStatus(status);
        skuMapper.updateById(sku);
        cacheService.evictSku(id);
        log.info("SKU 状态变更成功 skuId={} status={}", id, status);
        return toView(sku);
    }

    /** 出售视图:读取父 SPU 的 status 一并返回。 */
    public SkuSellView getSellView(Long skuId) {
        return cacheService.getSellView(skuId);
    }

    public SkuView get(Long id) {
        return toView(requireSku(id));
    }

    private Sku requireSku(Long id) {
        Sku sku = skuMapper.selectById4View(id);
        if (sku == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return sku;
    }

    private SkuView toView(Sku sku) {
        SkuView v = new SkuView();
        v.setId(sku.getId());
        v.setSpuId(sku.getSpuId());
        v.setSkuCode(sku.getSkuCode());
        v.setName(sku.getName());
        v.setPrice(sku.getPrice());
        v.setImage(sku.getImage());
        v.setSpecs(sku.getSpecs());
        v.setStatus(sku.getStatus());
        return v;
    }
}
