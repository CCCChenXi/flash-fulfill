package com.flash.fulfill.relay.redis;

import com.flash.fulfill.common.constant.RedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;

/**
 * Redis Stream 消费容器配置。
 * <p>
 * 用 StreamMessageListenerContainer(后台线程,对应伪代码 while(true){read→send→ack})监听秒杀建单事件,
 * 手动 ACK:监听器转发 RocketMQ 成功后才 ACK,失败留在 pending 待容器重投。
 */
@Slf4j
@Configuration
public class OrderEventRelay {

    private final StringRedisTemplate redisTemplate;
    private final String group;
    private final String consumerName;

    public OrderEventRelay(StringRedisTemplate redisTemplate,
                           @Value("${relay.stream.group:" + RedisKeys.SECKILL_EVENT_GROUP + "}") String group,
                           @Value("${relay.stream.consumer:flash-relay-01}") String consumerName) {
        this.redisTemplate = redisTemplate;
        this.group = group;
        this.consumerName = consumerName;
    }

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamContainer(
            RedisConnectionFactory connectionFactory) {
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .batchSize(10)
                        .build();
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);
        container.start();
        return container;
    }

    @Bean
    public Subscription orderEventSubscription(
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
            RocketMQTemplate rocketMQTemplate,
            RedisConnectionFactory connectionFactory) {
        ensureGroup(connectionFactory);

        OrderEventStreamListener listener = new OrderEventStreamListener(
                rocketMQTemplate, redisTemplate.opsForStream(),
                RedisKeys.SECKILL_EVENT_STREAM, group);

        StreamOffset<String> offset = StreamOffset.create(
                RedisKeys.SECKILL_EVENT_STREAM, ReadOffset.lastConsumed());
        Consumer consumer = Consumer.from(group, consumerName);
        return container.receive(consumer, offset, listener);
    }

    /** 消费组不存在时创建(幂等:重复创建会抛错,这里捕获)。 */
    private void ensureGroup(RedisConnectionFactory connectionFactory) {
        try {
            redisTemplate.opsForStream().createGroup(RedisKeys.SECKILL_EVENT_STREAM, group);
            log.info("秒杀事件 Stream 消费组已创建 group={}", group);
        } catch (Exception e) {
            log.info("秒杀事件 Stream 消费组已存在或创建失败(可忽略) group={} err={}", group, e.getMessage());
        }
    }
}