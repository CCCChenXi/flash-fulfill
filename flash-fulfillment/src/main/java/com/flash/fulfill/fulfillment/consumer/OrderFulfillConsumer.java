package com.flash.fulfill.fulfillment.consumer;

import com.flash.fulfill.common.constant.MqTopics;
import com.flash.fulfill.common.dto.OrderFulfillEvent;
import com.flash.fulfill.fulfillment.service.FulfillmentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 履约事件消费者:收到已创建订单后触发智能派单。
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MqTopics.ORDER_FULFILL,
        consumerGroup = MqTopics.GROUP_FULFILL_CONSUMER,
        selectorExpression = MqTopics.TAG_FULFILL
)
public class OrderFulfillConsumer implements RocketMQListener<OrderFulfillEvent> {

    private final FulfillmentService fulfillmentService;

    public OrderFulfillConsumer(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    @Override
    public void onMessage(OrderFulfillEvent event) {
        try {
            fulfillmentService.dispatch(event);
        } catch (Exception e) {
            log.error("处理履约事件异常 orderNo={}", event == null ? null : event.getOrderNo(), e);
            throw e;
        }
    }
}