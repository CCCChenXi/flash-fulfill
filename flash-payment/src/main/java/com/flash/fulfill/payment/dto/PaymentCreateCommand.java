package com.flash.fulfill.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 发起支付命令(客户端 -> 支付服务)。
 */
@Data
public class PaymentCreateCommand {

    @NotBlank(message = "orderNo 为必填")
    private String orderNo;

    @NotNull(message = "userId 为必填")
    private Long userId;

    @NotNull(message = "amount 为必填")
    @DecimalMin(value = "0.01", message = "amount 需大于 0")
    private BigDecimal amount;

    /** 支付渠道,默认 MOCK */
    private String channel;
}