package com.flash.fulfill.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒抢下单命令(seckill -> order,经 RocketMQ)。
 * requestId 为客户端幂等键,order 侧据此做幂等去重。
 * spuId 为商品 SPU 标识,秒杀侧据此校验 SPU 状态(key 定位)。
 * 商品单价在秒杀 Lua 内从 Redis(seckill:price:{skuId})读取,随 Redis Stream 多字段写入,
 * 经 relay 透传到订单服务,订单侧以消息内 price 计价,不再自取价格。
 * 收货地址不在秒杀命令内,订单生成后由用户在订单详情页填写,确认支付时提交到订单服务。
 */
@Data
public class SeckillOrderCommand implements Serializable {

    /** 幂等键,必填(由客户端携带) */
    @NotBlank(message = "requestId 为必填")
    private String requestId;

    @NotNull(message = "userId 为必填")
    private Long userId;

    @NotNull(message = "skuId 为必填")
    private Long skuId;

    @NotNull(message = "spuId 为必填")
    private Long spuId;

    private Long activityId;

    @NotNull(message = "quantity 为必填")
    @Min(value = 1, message = "quantity 需大于 0")
    private Integer quantity;

    /** 商品单价,秒杀 Lua 从 Redis 读取后写入事件,必填 */
    @NotNull(message = "price 为必填")
    private BigDecimal price;
}