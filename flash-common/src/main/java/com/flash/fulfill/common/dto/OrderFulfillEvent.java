package com.flash.fulfill.common.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单履约事件(order -> fulfillment,经 RocketMQ)。
 */
@Data
public class OrderFulfillEvent implements Serializable {

    private String orderNo;
    private Long userId;
    private Long skuId;
    private Integer quantity;
}