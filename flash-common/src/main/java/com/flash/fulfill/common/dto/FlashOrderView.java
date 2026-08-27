package com.flash.fulfill.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单查询视图。
 */
@Data
public class FlashOrderView implements Serializable {

    private String orderNo;
    private Long userId;
    private Long skuId;
    private Integer quantity;
    private BigDecimal amount;
    private String status;
}