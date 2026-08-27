package com.flash.fulfill.product.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * SKU 视图。
 */
@Data
public class SkuView {

    private Long id;

    private Long spuId;

    private String skuCode;

    private String name;

    private BigDecimal price;

    private String image;

    private String specs;

    private Integer status;
}
