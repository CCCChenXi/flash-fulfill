package com.flash.fulfill.relay;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

/**
 * 事件转发服务入口。
 * <p>
 * 消费 Redis Stream 中的秒杀建单事件(多字段),构建建单命令转发到 RocketMQ [FLASH_ORDER_CREATE]。
 * 订单侧 Outbox 转发由 flash-order 自维护,不再经本服务。
 */
@SpringBootApplication
@EnableDiscoveryClient
@Import(RocketMQAutoConfiguration.class)
public class RelayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RelayApplication.class, args);
    }
}