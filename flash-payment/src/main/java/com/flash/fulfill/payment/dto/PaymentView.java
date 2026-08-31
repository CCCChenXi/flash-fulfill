package com.flash.fulfill.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付单视图。
 */
@Data
public class PaymentView {

    private String payNo;
    private String orderNo;
    private BigDecimal amount;
    private String channel;
    private String status;
}