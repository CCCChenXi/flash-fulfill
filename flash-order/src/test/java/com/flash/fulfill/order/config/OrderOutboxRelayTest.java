package com.flash.fulfill.order.config;

import com.flash.fulfill.common.constant.MqTopics;
import com.flash.fulfill.order.entity.OrderOutbox;
import com.flash.fulfill.order.mapper.OrderOutboxMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderOutboxRelayTest {

    private OrderOutboxMapper outboxMapper;
    private RocketMQTemplate rocketMQTemplate;
    private OrderOutboxRelay relay;

    @BeforeEach
    void setUp() {
        outboxMapper = mock(OrderOutboxMapper.class);
        rocketMQTemplate = mock(RocketMQTemplate.class);
        relay = new OrderOutboxRelay(outboxMapper, rocketMQTemplate);
    }

    private OrderOutbox buildRecord(Long id, String type) {
        OrderOutbox r = new OrderOutbox();
        r.setId(id);
        r.setMsgType(type);
        r.setMsgBody("{\"x\":1}");
        return r;
    }

    @Test
    void sendsDeductToInventoryTopicAndMarksSent() {
        when(outboxMapper.selectPending(anyInt())).thenReturn(List.of(buildRecord(1L, "DEDUCT")));

        relay.relayPending();

        verify(rocketMQTemplate).syncSend(
                eq(MqTopics.INVENTORY_DEDUCT + ":" + MqTopics.TAG_DEDUCT),
                eq("{\"x\":1}"),
                eq(MqTopics.SEND_TIMEOUT_MILLIS));
        verify(outboxMapper).markSent(1L);
    }

    @Test
    void sendsFulfillToOrderFulfillTopicAndMarksSent() {
        when(outboxMapper.selectPending(anyInt())).thenReturn(List.of(buildRecord(1L, "FULFILL")));

        relay.relayPending();

        verify(rocketMQTemplate).syncSend(
                eq(MqTopics.ORDER_FULFILL + ":" + MqTopics.TAG_FULFILL),
                eq("{\"x\":1}"),
                eq(MqTopics.SEND_TIMEOUT_MILLIS));
        verify(outboxMapper).markSent(1L);
    }

    @Test
    void marksUnknownTypeSentWithoutSending() {
        when(outboxMapper.selectPending(anyInt())).thenReturn(List.of(buildRecord(1L, "UNKNOWN")));

        relay.relayPending();

        verify(rocketMQTemplate, never()).syncSend(anyString(), any(Object.class), anyLong());
        verify(outboxMapper).markSent(1L);
    }

    @Test
    void incrementsRetryOnSendFailure() {
        when(outboxMapper.selectPending(anyInt())).thenReturn(List.of(buildRecord(1L, "DEDUCT")));
        when(rocketMQTemplate.syncSend(anyString(), any(Object.class), anyLong()))
                .thenThrow(new RuntimeException("mq down"));

        relay.relayPending();

        verify(outboxMapper).incrementRetry(1L);
        verify(outboxMapper, never()).markSent(1L);
    }
}