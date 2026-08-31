package com.flash.fulfill.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 支付网关异步回调命令(模拟网关 -> 支付服务)。
 */
@Data
public class PaymentCallbackCommand {

    @NotBlank(message = "payNo 为必填")
    private String payNo;

    /** 是否支付成功 */
    @NotNull(message = "success 为必填")
    private Boolean success;
}