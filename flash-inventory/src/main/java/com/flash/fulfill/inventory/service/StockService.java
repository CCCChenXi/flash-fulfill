package com.flash.fulfill.inventory.service;

import com.flash.fulfill.common.api.ErrorCode;
import com.flash.fulfill.common.dto.DeductStockCommand;
import com.flash.fulfill.common.exception.BizException;
import com.flash.fulfill.inventory.entity.Stock;
import com.flash.fulfill.inventory.entity.StockFlow;
import com.flash.fulfill.inventory.mapper.StockFlowMapper;
import com.flash.fulfill.inventory.mapper.StockMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存服务:消费扣减命令(MQ),流水表幂等后执行扣减。
 * <p>
 * 扣减命令经 RocketMQ 投递,可能被重复消费;以 stock_flow.request_id 唯一键 + 幂等判断保证只扣一次。
 * 库存不足为业务失败,写入 FAILED 流水后正常返回(不抛异常),由订单侧对账处理。
 */
@Slf4j
@Service
public class StockService {

    public static final String FLOW_RESULT_SUCCESS = "SUCCESS";
    public static final String FLOW_RESULT_FAILED = "FAILED";

    private final StockMapper stockMapper;
    private final StockFlowMapper stockFlowMapper;

    public StockService(StockMapper stockMapper, StockFlowMapper stockFlowMapper) {
        this.stockMapper = stockMapper;
        this.stockFlowMapper = stockFlowMapper;
    }

    /**
     * 处理扣减命令:幂等判断 + 条件扣减 + 写流水(同一事务)。
     * 并发重复消息由 stock_flow.uk_request_id 兜底,消费者捕获 DuplicateKeyException 忽略。
     */
    @Transactional
    public void processDeduct(DeductStockCommand cmd) {
        if (cmd == null || cmd.getSkuId() == null || cmd.getQuantity() == null || cmd.getQuantity() <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAM, "扣减参数不合法");
        }
        if (stockFlowMapper.existsByRequestId(cmd.getRequestId())) {
            log.info("重复扣减命令已忽略 requestId={}", cmd.getRequestId());
            return;
        }

        int rows = stockMapper.deductStock(cmd.getSkuId(), cmd.getQuantity());
        boolean success = rows > 0;

        StockFlow flow = new StockFlow();
        flow.setRequestId(cmd.getRequestId());
        flow.setOrderNo(cmd.getOrderNo());
        flow.setSkuId(cmd.getSkuId());
        flow.setQuantity(cmd.getQuantity());
        flow.setResult(success ? FLOW_RESULT_SUCCESS : FLOW_RESULT_FAILED);
        stockFlowMapper.insert(flow);

        if (success) {
            log.info("库存扣减成功 skuId={} qty={} requestId={}",
                    cmd.getSkuId(), cmd.getQuantity(), cmd.getRequestId());
        } else {
            log.warn("库存不足 skuId={} qty={} requestId={}",
                    cmd.getSkuId(), cmd.getQuantity(), cmd.getRequestId());
        }
    }

    public Stock query(Long skuId) {
        Stock stock = stockMapper.selectBySkuId(skuId);
        if (stock == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "SKU 不存在:" + skuId);
        }
        return stock;
    }
}