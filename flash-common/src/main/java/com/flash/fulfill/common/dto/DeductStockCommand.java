package com.flash.fulfill.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 库存扣减命令(order -> inventory,经 OpenFeign)。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeductStockCommand implements Serializable {

    /** 幂等键 */
    private String requestId;
    /** 关联订单号 */
    private String orderNo;
    private Long skuId;
    private Integer quantity;
}