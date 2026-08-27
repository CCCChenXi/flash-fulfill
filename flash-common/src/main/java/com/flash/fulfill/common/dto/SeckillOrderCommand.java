package com.flash.fulfill.common.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 秒抢下单命令(seckill -> order,经 RocketMQ)。
 * requestId 为客户端幂等键,order 侧据此做幂等去重。
 */
@Data
public class SeckillOrderCommand implements Serializable {

    private String requestId;
    private Long userId;
    private Long skuId;
    private Long activityId;
    private Integer quantity;
}