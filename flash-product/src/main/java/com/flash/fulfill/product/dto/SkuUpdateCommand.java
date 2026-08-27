package com.flash.fulfill.product.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 更新 SKU 请求(部分更新,字段可选;null 视为不修改)。
 */
@Data
@NoArgsConstructor
public class SkuUpdateCommand {

    private String name;

    private BigDecimal price;

    private String image;

    private String specs;

    private Integer status;
}
