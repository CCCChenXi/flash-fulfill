package com.flash.fulfill.fulfillment;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

/**
 * 履约中心入口。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@Import(RocketMQAutoConfiguration.class)
@MapperScan("com.flash.fulfill.fulfillment.mapper")
public class FulfillmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(FulfillmentApplication.class, args);
    }
}