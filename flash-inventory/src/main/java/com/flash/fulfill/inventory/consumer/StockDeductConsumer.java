package com.flash.fulfill.inventory.consumer;

import com.flash.fulfill.common.constant.MqTopics;
import com.flash.fulfill.common.dto.DeductStockCommand;
import com.flash.fulfill.inventory.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 扣减库存命令消费者(order 经 flash-relay 投递)。
 * <p>
 * 幂等由 stock_flow.uk_request_id 唯一键保证:重复消息触发 DuplicateKeyException 视为已处理,
 * 直接忽略不重试;其余异常交由 RocketMQ 重试机制处理。
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MqTopics.INVENTORY_DEDUCT,
        consumerGroup = MqTopics.GROUP_INVENTORY_DEDUCT_CONSUMER,
        selectorExpression = MqTopics.TAG_DEDUCT
)
public class StockDeductConsumer implements RocketMQListener<DeductStockCommand> {

    private final StockService stockService;

    public StockDeductConsumer(StockService stockService) {
        this.stockService = stockService;
    }

    @Override
    public void onMessage(DeductStockCommand cmd) {
        try {
            stockService.processDeduct(cmd);
        } catch (DuplicateKeyException e) {
            log.info("重复扣减命令已忽略 requestId={}", cmd == null ? null : cmd.getRequestId());
        } catch (Exception e) {
            log.error("处理扣减命令异常 requestId={}", cmd == null ? null : cmd.getRequestId(), e);
            throw e;
        }
    }
}