package com.flash.fulfill.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SKU 出售视图:下单 / 秒杀等下游服务跨服务消费。
 */
@Data
public class SkuSellView implements Serializable {

    private Long skuId;

    private Long spuId;

    private String skuName;

    private BigDecimal price;

    private Integer skuStatus;

    private Integer spuStatus;
}
