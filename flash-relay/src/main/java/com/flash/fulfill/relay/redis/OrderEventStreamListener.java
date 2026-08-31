package com.flash.fulfill.relay.redis;

import com.flash.fulfill.common.constant.MqTopics;
import com.flash.fulfill.common.dto.SeckillOrderCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.stream.StreamListener;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Redis Stream 建单事件监听器。
 * <p>
 * 事件以多 field-value 存储(requestId/userId/skuId/spuId/activityId/quantity/price,无 JSON),
 * 收到后逐字段构建 {@link SeckillOrderCommand} 转发 RocketMQ [FLASH_ORDER_CREATE],成功后 ACK;
 * 发送失败不 ACK,事件留在 pending 中由容器重读重发。
 */
@Slf4j
public class OrderEventStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final RocketMQTemplate rocketMQTemplate;
    private final StreamOperations<String, ?, ?> streamOperations;
    private final String streamKey;
    private final String group;

    public OrderEventStreamListener(RocketMQTemplate rocketMQTemplate,
                                    StreamOperations<String, ?, ?> streamOperations,
                                    String streamKey,
                                    String group) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.streamOperations = streamOperations;
        this.streamKey = streamKey;
        this.group = group;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        Map<String, String> fields = record.getValue();
        if (fields == null || fields.isEmpty()) {
            log.warn("秒杀事件内容为空,ACK 跳过 streamId={}", record.getId());
            ack(record.getId());
            return;
        }
        SeckillOrderCommand cmd;
        try {
            cmd = toCommand(fields);
        } catch (IllegalArgumentException e) {
            // 字段缺失/非法的事件无需重试,ACK 掉避免卡死队列
            log.error("秒杀事件字段缺失或非法,ACK 跳过 streamId={} err={}", record.getId(), e.getMessage());
            ack(record.getId());
            return;
        }

        String destination = MqTopics.FLASH_ORDER_CREATE + ":" + MqTopics.TAG_ORDER_CREATE;
        try {
            rocketMQTemplate.syncSend(destination, cmd, MqTopics.SEND_TIMEOUT_MILLIS);
            log.info("秒杀事件已转发 RocketMQ streamId={} requestId={}", record.getId(), cmd.getRequestId());
            ack(record.getId());
        } catch (Exception e) {
            // 发送失败:不 ACK,留在 pending 待容器重读重发
            log.error("秒杀事件转发 RocketMQ 失败 streamId={} requestId={}", record.getId(), cmd.getRequestId(), e);
        }
    }

    private SeckillOrderCommand toCommand(Map<String, String> fields) {
        SeckillOrderCommand cmd = new SeckillOrderCommand();
        cmd.setRequestId(requireField(fields, "requestId"));
        cmd.setUserId(requireLong(fields, "userId"));
        cmd.setSkuId(requireLong(fields, "skuId"));
        cmd.setSpuId(requireLong(fields, "spuId"));
        cmd.setQuantity(requireInt(fields, "quantity"));
        cmd.setPrice(requirePrice(fields, "price"));
        String activityId = fields.get("activityId");
        cmd.setActivityId(activityId == null || activityId.isBlank() ? null : Long.valueOf(activityId));
        return cmd;
    }

    private String requireField(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少字段 " + name);
        }
        return value;
    }

    private Long requireLong(Map<String, String> fields, String name) {
        try {
            return Long.valueOf(requireField(fields, name));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("字段 " + name + " 非法", e);
        }
    }

    private Integer requireInt(Map<String, String> fields, String name) {
        try {
            return Integer.valueOf(requireField(fields, name));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("字段 " + name + " 非法", e);
        }
    }

    private BigDecimal requirePrice(Map<String, String> fields, String name) {
        try {
            return new BigDecimal(requireField(fields, name));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("字段 " + name + " 非法", e);
        }
    }

    private void ack(RecordId recordId) {
        try {
            streamOperations.acknowledge(streamKey, group, recordId);
        } catch (Exception e) {
            log.error("秒杀事件 ACK 失败 streamId={}", recordId, e);
        }
    }
}