package com.flash.fulfill.order.config;

import com.flash.fulfill.common.constant.MqTopics;
import com.flash.fulfill.order.entity.OrderOutbox;
import com.flash.fulfill.order.mapper.OrderOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 订单本地消息表(Outbox)轮询转发器。
 * <p>
 * 定时轮询 order_outbox 中 PENDING 记录,按 msg_type 转发 RocketMQ:
 * DEDUCT → INVENTORY_DEDUCT(库存扣减命令)、FULFILL → ORDER_FULFILL(履约事件)。
 * 发送成功置 SENT,失败累加重试计数保留 PENDING 下轮重试。
 * <p>
 * selectPending 使用 FOR UPDATE SKIP LOCKED,多实例部署时同一条消息仅被一个实例处理。
 */
@Slf4j
@Component
public class OrderOutboxRelay {

    private final OrderOutboxMapper outboxMapper;
    private final RocketMQTemplate rocketMQTemplate;

    @Value("${order.outbox.batch-size:50}")
    private int batchSize;

    public OrderOutboxRelay(OrderOutboxMapper outboxMapper, RocketMQTemplate rocketMQTemplate) {
        this.outboxMapper = outboxMapper;
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Scheduled(fixedDelayString = "${order.outbox.poll-interval-ms:1000}")
    @Transactional
    public void relayPending() {
        List<OrderOutbox> batch = outboxMapper.selectPending(batchSize);
        if (batch.isEmpty()) {
            return;
        }
        for (OrderOutbox record : batch) {
            String destination = destinationOf(record.getMsgType());
            if (destination == null) {
                log.warn("未知 outbox 消息类型,置 SENT 跳过 msgType={} id={}", record.getMsgType(), record.getId());
                outboxMapper.markSent(record.getId());
                continue;
            }
            try {
                rocketMQTemplate.syncSend(destination, record.getMsgBody(), MqTopics.SEND_TIMEOUT_MILLIS);
                outboxMapper.markSent(record.getId());
                log.info("outbox 消息已发送 id={} msgType={} dest={}", record.getId(), record.getMsgType(), destination);
            } catch (Exception e) {
                outboxMapper.incrementRetry(record.getId());
                log.error("outbox 消息发送失败 id={} msgType={} dest={}", record.getId(), record.getMsgType(), destination, e);
            }
        }
    }

    private String destinationOf(String msgType) {
        if ("DEDUCT".equals(msgType)) {
            return MqTopics.INVENTORY_DEDUCT + ":" + MqTopics.TAG_DEDUCT;
        }
        if ("FULFILL".equals(msgType)) {
            return MqTopics.ORDER_FULFILL + ":" + MqTopics.TAG_FULFILL;
        }
        return null;
    }
}