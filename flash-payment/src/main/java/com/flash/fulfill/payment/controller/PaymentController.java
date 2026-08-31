package com.flash.fulfill.payment.controller;

import com.flash.fulfill.common.api.Result;
import com.flash.fulfill.common.constant.ApiPaths;
import com.flash.fulfill.payment.dto.PaymentCallbackCommand;
import com.flash.fulfill.payment.dto.PaymentCreateCommand;
import com.flash.fulfill.payment.dto.PaymentView;
import com.flash.fulfill.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口。
 */
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 发起支付:返回支付单号(待支付),等待网关异步回调。 */
    @PostMapping(ApiPaths.PAYMENT_PAY)
    public Result<PaymentView> pay(@Valid @RequestBody PaymentCreateCommand cmd) {
        return Result.ok(paymentService.createPayment(cmd));
    }

    /** 模拟支付网关异步回调。 */
    @PostMapping(ApiPaths.PAYMENT_CALLBACK)
    public Result<PaymentView> callback(@Valid @RequestBody PaymentCallbackCommand cmd) {
        return Result.ok(paymentService.handleCallback(cmd));
    }

    /** 按订单号查询支付单状态。 */
    @GetMapping(ApiPaths.PAYMENT_QUERY)
    public Result<PaymentView> query(@PathVariable("orderNo") String orderNo) {
        return Result.ok(paymentService.queryByOrderNo(orderNo));
    }
}