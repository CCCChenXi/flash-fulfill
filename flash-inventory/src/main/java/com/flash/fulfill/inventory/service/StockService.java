package com.flash.fulfill.inventory.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.dto.DeductStockCommand;
import com.flash.fulfill.common.dto.DeductStockResult;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.inventory.entity.Stock;
import com.flash.fulfill.inventory.mapper.StockMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存服务:核心扣减逻辑。
 */
@Slf4j
@Service
public class StockService {

    private final StockMapper stockMapper;

    public StockService(StockMapper stockMapper) {
        this.stockMapper = stockMapper;
    }

    /**
     * 扣减库存:原子条件更新,库存不足返回 success=false(不抛异常)。
     */
    @Transactional
    public DeductStockResult deduct(DeductStockCommand cmd) {
        if (cmd == null || cmd.getSkuId() == null || cmd.getQuantity() == null || cmd.getQuantity() <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAM, "扣减参数不合法");
        }
        int rows = stockMapper.deductStock(cmd.getSkuId(), cmd.getQuantity());
        if (rows > 0) {
            Stock after = stockMapper.selectBySkuId(cmd.getSkuId());
            Integer remaining = after == null ? null : after.getAvailable();
            log.info("库存扣减成功 skuId={} qty={} 剩余={}",
                    cmd.getSkuId(), cmd.getQuantity(), remaining);
            return new DeductStockResult(true, remaining);
        }
        log.warn("库存不足 skuId={} qty={}", cmd.getSkuId(), cmd.getQuantity());
        return new DeductStockResult(false, null);
    }

    public Stock query(Long skuId) {
        Stock stock = stockMapper.selectBySkuId(skuId);
        if (stock == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "SKU 不存在:" + skuId);
        }
        return stock;
    }
}
