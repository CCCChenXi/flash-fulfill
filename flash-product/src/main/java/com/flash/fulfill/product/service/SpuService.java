package com.flash.fulfill.product.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.product.cache.SkuCacheService;
import com.flash.fulfill.product.dto.SpuCreateCommand;
import com.flash.fulfill.product.dto.SpuUpdateCommand;
import com.flash.fulfill.product.dto.SpuView;
import com.flash.fulfill.product.entity.Spu;
import com.flash.fulfill.product.mapper.SpuMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * SPU 服务:新建 / 取用 / 更新 / 上下架。
 */
@Slf4j
@Service
public class SpuService {

    public static final int STATUS_ON_SHELF = 1;
    public static final int STATUS_OFF_SHELF = 0;

    private final SpuMapper spuMapper;
    private final SkuCacheService cacheService;

    public SpuService(SpuMapper spuMapper, SkuCacheService cacheService) {
        this.spuMapper = spuMapper;
        this.cacheService = cacheService;
    }

    /** 新建 SPU,默认上架。 */
    @Transactional
    public SpuView create(SpuCreateCommand cmd) {
        Spu spu = new Spu();
        spu.setName(cmd.getName());
        spu.setCategoryId(cmd.getCategoryId());
        spu.setBrandId(cmd.getBrandId());
        spu.setDescription(cmd.getDescription());
        spu.setMainImage(cmd.getMainImage());
        spu.setStatus(STATUS_ON_SHELF);
        spuMapper.insert(spu);
        log.info("新建 SPU 成功 spuId={}", spu.getId());
        return toView(spu);
    }

    /** 部分更新:传入的字段为 null 时不修改。 */
    @Transactional
    public SpuView update(Long id, SpuUpdateCommand cmd) {
        Spu spu = requireSpu(id);
        if (cmd.getName() != null) {
            spu.setName(cmd.getName());
        }
        if (cmd.getCategoryId() != null) {
            spu.setCategoryId(cmd.getCategoryId());
        }
        if (cmd.getBrandId() != null) {
            spu.setBrandId(cmd.getBrandId());
        }
        if (cmd.getDescription() != null) {
            spu.setDescription(cmd.getDescription());
        }
        if (cmd.getMainImage() != null) {
            spu.setMainImage(cmd.getMainImage());
        }
        spuMapper.updateById(spu);
        evictAfterCommit(() -> cacheService.evictSpu(id));
        log.info("更新 SPU 成功 spuId={}", id);
        return toView(spu);
    }

    /** 上架。 */
    @Transactional
    public SpuView onShelf(Long id) {
        return setStatus(id, STATUS_ON_SHELF);
    }

    /** 下架。 */
    @Transactional
    public SpuView offShelf(Long id) {
        return setStatus(id, STATUS_OFF_SHELF);
    }

    @Transactional
    public SpuView get(Long id) {
        return toView(requireSpu(id));
    }

    private SpuView setStatus(Long id, int status) {
        Spu spu = requireSpu(id);
        spu.setStatus(status);
        spuMapper.updateById(spu);
        evictAfterCommit(() -> cacheService.evictSpu(id));
        log.info("SPU 状态变更成功 spuId={} status={}", id, status);
        return toView(spu);
    }

    private Spu requireSpu(Long id) {
        Spu spu = spuMapper.selectById4View(id);
        if (spu == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return spu;
    }

    /** 事务提交后再驱逐缓存,避免并发读在提交前回填旧数据;非事务环境下立即驱逐。 */
    private void evictAfterCommit(Runnable evict) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }

    private SpuView toView(Spu spu) {
        SpuView v = new SpuView();
        v.setId(spu.getId());
        v.setName(spu.getName());
        v.setCategoryId(spu.getCategoryId());
        v.setBrandId(spu.getBrandId());
        v.setDescription(spu.getDescription());
        v.setMainImage(spu.getMainImage());
        v.setStatus(spu.getStatus());
        return v;
    }
}
