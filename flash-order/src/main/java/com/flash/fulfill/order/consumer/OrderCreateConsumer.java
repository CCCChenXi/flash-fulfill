package com.flash.fulfill.order.consumer;

import com.flash.fulfill.common.constant.MqTopics;
import com.flash.fulfill.common.dto.SeckillOrderCommand;
import com.flash.fulfill.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 秒抢建单命令消费者:收到消息后异步创建订单(订单 + 本地消息表同事务)。
 * <p>
 * 幂等由 flash_order.uk_request_id 唯一键保证:重复消息触发 DuplicateKeyException 视为已处理,
 * 直接忽略不重试;其余异常交由 RocketMQ 重试机制处理。
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MqTopics.FLASH_ORDER_CREATE,
        consumerGroup = MqTopics.GROUP_ORDER_CREATE_CONSUMER,
        selectorExpression = MqTopics.TAG_ORDER_CREATE
)
public class OrderCreateConsumer implements RocketMQListener<SeckillOrderCommand> {

    private final OrderService orderService;

    public OrderCreateConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void onMessage(SeckillOrderCommand cmd) {
        try {
            orderService.handleOrderCreate(cmd);
        } catch (DuplicateKeyException e) {
            log.info("重复建单已忽略 requestId={}", cmd == null ? null : cmd.getRequestId());
        } catch (Exception e) {
            log.error("处理建单命令异常 requestId={}", cmd == null ? null : cmd.getRequestId(), e);
            throw e;
        }
    }
}