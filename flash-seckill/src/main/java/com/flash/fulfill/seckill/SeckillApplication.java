package com.flash.fulfill.seckill;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

/**
 * 秒抢服务入口。
 * <p>
 * rocketmq-spring-boot-starter 2.3.x 起需显式导入 RocketMQAutoConfiguration。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@Import(RocketMQAutoConfiguration.class)
public class SeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillApplication.class, args);
    }
}