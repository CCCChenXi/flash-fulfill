package com.flash.fulfill.relay.redis;

import com.flash.fulfill.common.constant.MqTopics;
import com.flash.fulfill.common.dto.SeckillOrderCommand;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderEventStreamListenerTest {

    private RocketMQTemplate rocketMQTemplate;
    @SuppressWarnings("unchecked")
    private StreamOperations<String, ?, ?> streamOperations;
    private OrderEventStreamListener listener;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        rocketMQTemplate = mock(RocketMQTemplate.class);
        streamOperations = mock(StreamOperations.class);
        listener = new OrderEventStreamListener(rocketMQTemplate,
                streamOperations, "seckill:event:order", "flash-seckill-order-relay");
    }

    private MapRecord<String, String, String> record(Map<String, String> fields) {
        return MapRecord.create("seckill:event:order", fields);
    }

    private Map<String, String> eventFields() {
        Map<String, String> fields = new HashMap<>();
        fields.put("requestId", "req-001");
        fields.put("userId", "1001");
        fields.put("skuId", "1001");
        fields.put("spuId", "1");
        fields.put("activityId", "1");
        fields.put("quantity", "1");
        fields.put("price", "99.00");
        return fields;
    }

    @Test
    void forwardsToRocketMqAndAcks() {
        assertDoesNotThrow(() -> listener.onMessage(record(eventFields())));

        ArgumentCaptor<SeckillOrderCommand> cmdCaptor = ArgumentCaptor.forClass(SeckillOrderCommand.class);
        verify(rocketMQTemplate).syncSend(
                eq(MqTopics.FLASH_ORDER_CREATE + ":" + MqTopics.TAG_ORDER_CREATE),
                cmdCaptor.capture(), anyLong());
        SeckillOrderCommand cmd = cmdCaptor.getValue();
        assertEquals("req-001", cmd.getRequestId());
        assertEquals(1001L, cmd.getUserId());
        assertEquals(1001L, cmd.getSkuId());
        assertEquals(1L, cmd.getSpuId());
        assertEquals(1L, cmd.getActivityId());
        assertEquals(1, cmd.getQuantity());
        assertEquals(0, new BigDecimal("99.00").compareTo(cmd.getPrice()));

        verify(streamOperations).acknowledge(eq("seckill:event:order"), anyString(), any(RecordId.class));
    }

    @Test
    void doesNotAckWhenSendFails() {
        MapRecord<String, String, String> rec = record(eventFields());
        when(rocketMQTemplate.syncSend(anyString(), any(Object.class), anyLong()))
                .thenThrow(new RuntimeException("mq down"));

        assertDoesNotThrow(() -> listener.onMessage(rec));

        verify(streamOperations, never()).acknowledge(eq("seckill:event:order"), anyString(), any(RecordId.class));
    }

    @Test
    void acksEventWithMissingRequiredFieldWithoutForwarding() {
        Map<String, String> fields = eventFields();
        fields.remove("requestId");

        assertDoesNotThrow(() -> listener.onMessage(record(fields)));

        verify(rocketMQTemplate, never()).syncSend(anyString(), any(Object.class), anyLong());
        verify(streamOperations).acknowledge(eq("seckill:event:order"), anyString(), any(RecordId.class));
    }

    @Test
    void parsesBlankActivityIdAsNull() {
        Map<String, String> fields = eventFields();
        fields.put("activityId", "");

        assertDoesNotThrow(() -> listener.onMessage(record(fields)));

        ArgumentCaptor<SeckillOrderCommand> cmdCaptor = ArgumentCaptor.forClass(SeckillOrderCommand.class);
        verify(rocketMQTemplate).syncSend(
                eq(MqTopics.FLASH_ORDER_CREATE + ":" + MqTopics.TAG_ORDER_CREATE),
                cmdCaptor.capture(), anyLong());
        assertEquals(null, cmdCaptor.getValue().getActivityId());
    }
}